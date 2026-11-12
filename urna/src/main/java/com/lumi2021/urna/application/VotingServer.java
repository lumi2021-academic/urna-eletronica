package com.lumi2021.urna.application;

import com.lumi2021.urna.domain.CandidateRepository;
import com.lumi2021.urna.domain.ElectorRepository;
import com.lumi2021.urna.domain.models.CandidateEntity;
import com.lumi2021.urna.infra.firebase.VotoRequest;
import com.lumi2021.urna.platform.events.InputAction;
import com.lumi2021.urna.view.models.MesarioScreenModel;
import com.lumi2021.urna.view.models.VotingScreenModel;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class VotingServer {

    public static final String VEREADOR = "VEREADOR";
    public static final String PREFEITO = "PREFEITO";
    private static final int ID_LENGTH = 12;
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final LocalTime CLOSING_TIME = LocalTime.of(17, 0);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public enum MesarioStep {
        ENTER_ID,
        CONFIRM_ID,
        AUTHORIZED,
        MENU,
        CLOSE_ID,
        CLOSED
    }

    public record Session(String sessionId, String electorId) { }
    public static final class RejectedException extends Exception {
        public RejectedException(String message) { super(message); }
    }

    private Session currentUrnaSession;

    private final CandidateRepository candidates;
    private final ElectorRepository electors;
    private final VotingGateway gateway;

    private final Set<String> voted = ConcurrentHashMap.newKeySet();
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    // Estado da Mesa do Mesário no Servidor
    private MesarioStep mesarioStep = MesarioStep.ENTER_ID;
    private final StringBuilder idInput = new StringBuilder();
    private String currentVoter = null;
    private int votesCount = 0;
    private int totalVoters = 0;
    private String presidentId;
    private String noticeText = null;
    private boolean released = false;
    private Clock clock = Clock.system(BRASILIA);

    // Callbacks de Output para as views remotas
    private Consumer<MesarioScreenModel> mesarioStateListener;
    private Consumer<VotingScreenModel> voterScreenListener;

    public VotingServer(CandidateRepository candidates, ElectorRepository electors, VotingGateway gateway) {
        this.candidates = candidates;
        this.electors = electors;
        this.gateway = gateway;
    }

    public void setMesarioStateListener(Consumer<MesarioScreenModel> listener) {
        this.mesarioStateListener = listener;
        publishMesarioState();
    }

    public void setVoterScreenListener(Consumer<VotingScreenModel> listener) {
        this.voterScreenListener = listener;
        publishVoterState(new VotingScreenModel.Waiting(null));
    }

    // ==================== PROCESSAMENTO DE INPUT DO MESÁRIO ====================

    public synchronized void handleMesarioInput(InputAction input) {
        clearNotice();
        switch (input) {
            case KEY_0, KEY_1, KEY_2,
                 KEY_3, KEY_4, KEY_5,
                 KEY_6, KEY_7, KEY_8,
                 KEY_9 -> pressDigit(InputAction.toInteger(input));
            case CORRIGE -> pressCorrige();
            case CONFIRMA -> pressConfirma();
        }
        publishMesarioState();
    }

    private void pressDigit(int digit) {
        switch (mesarioStep) {
            case ENTER_ID,
                 CLOSE_ID -> {
                if (idInput.length() < ID_LENGTH) idInput.append(digit);
            }

            case MENU -> {
                if (digit == 1) {
                    mesarioStep = MesarioStep.ENTER_ID;
                }

                else if (digit == 2) {
                    if (LocalTime.now(clock).isBefore(CLOSING_TIME)) {
                        setNotice("Só após as 17h (Brasília).");
                    }
                    else {
                        idInput.setLength(0);
                        mesarioStep = MesarioStep.CLOSE_ID;
                    }
                }

                else {
                    setNotice("Opção inválida.");
                }
            }

            default -> {}
        }
    }

    private void pressCorrige() {
        switch (mesarioStep) {
            case ENTER_ID,
                 CLOSE_ID -> {
                if (idInput.isEmpty()) mesarioStep = MesarioStep.MENU;
                else idInput.setLength(0);
            }

            case CONFIRM_ID -> {
                idInput.setLength(0);
                mesarioStep = MesarioStep.ENTER_ID;
            }

            case AUTHORIZED -> {
                if (!released) {
                    setNotice("Voto em andamento.");
                    break;
                }
                cancelCurrentSession();
            }

            case MENU -> mesarioStep = MesarioStep.ENTER_ID;

            default -> {}
        }
    }

    private void pressConfirma() {
        switch (mesarioStep) {
            case ENTER_ID -> {
                if (idInput.length() < ID_LENGTH) {
                    setNotice("Título incompleto.");
                }
                else if (voted.contains(idInput.toString())) {
                    setNotice("Este eleitor já votou.");
                }
                else {
                    mesarioStep = MesarioStep.CONFIRM_ID;
                }
            }

            case CONFIRM_ID -> {
                String electorId = idInput.toString();

                try {
                    currentUrnaSession = authorize(electorId);
                    currentVoter = electorId;
                    released = true;
                    mesarioStep = MesarioStep.AUTHORIZED;

                    publishVoterState(new VotingScreenModel.Voting(
                            VEREADOR,
                            5,
                            "",
                            null,
                            null,
                            null,
                            null,
                            null)
                    );

                } catch (RejectedException e) {
                    setNotice(e.getMessage());
                    mesarioStep = MesarioStep.ENTER_ID;
                    idInput.setLength(0);
                }
            }

            case CLOSE_ID -> {
                if (idInput.length() < ID_LENGTH) {
                    setNotice("Título incompleto.");
                }

                else if (presidentId != null && !presidentId.contentEquals(idInput)) {
                    setNotice("Título não autorizado.");
                }

                else {
                    idInput.setLength(0);
                    mesarioStep = MesarioStep.CLOSED;
                }
            }
            default -> {}
        }
    }

    public synchronized void cancelCurrentSession() {
        if (currentVoter != null) {
            inProgress.remove(currentVoter);
            currentVoter = null;
        }

        released = false;
        idInput.setLength(0);
        mesarioStep = MesarioStep.ENTER_ID;
        setNotice("Liberação cancelada.");
        publishMesarioState();
        publishVoterState(new VotingScreenModel.Waiting(null));
    }

    // ==================== PROCESSAMENTO DE VOTAÇÃO E SESSÃO ====================

    public synchronized void iniciarSessaoEleitoral(String eleicaoID) throws Exception {
        if (currentUrnaSession != null) return;

        try {
            gateway.abrirEleicao(eleicaoID);
            String sessionId = gateway.abrirSessao();
            this.currentUrnaSession = new Session(sessionId, null);
            System.out.println("Sessão eleitoral iniciada no gateway com ID: " + sessionId);
        }
        catch (Exception e) {
            throw new Exception("Falha ao abrir a sessão eleitoral no gateway: " + describe(e), e);
        }
    }

    public Session authorize(String electorId) throws RejectedException {

        if (!electors.isElectorIdValid(electorId)) throw new RejectedException("Título inválido ou inativo.");
        if (voted.contains(electorId)) throw new RejectedException("Este eleitor já votou.");
        if (!inProgress.add(electorId)) throw new RejectedException("Este eleitor já foi liberado.");

        try {
            return new Session(gateway.abrirSessao(), electorId);
        }

        catch (Exception e) {
            inProgress.remove(electorId);
            throw new RejectedException("Falha ao abrir sessão: " + describe(e));
        }
    }

    public void confirm(Session session, VotoRequest vereador, VotoRequest prefeito) throws RejectedException {
        if (session == null || !inProgress.contains(session.electorId())) {
            throw new RejectedException("Sessão de votação não está ativa.");
        }
        try {
            publishVoterState(new VotingScreenModel.Submitting());
            gateway.registrar(session.sessionId(), List.of(vereador, prefeito));

            voted.add(session.electorId());
            inProgress.remove(session.electorId());
            votesCount++;

            currentVoter = null;
            released = false;
            idInput.setLength(0);
            mesarioStep = MesarioStep.ENTER_ID;
            setNotice("Voto registrado.");

            publishMesarioState();
            publishVoterState(new VotingScreenModel.Finished());
        } catch (Exception e) {
            inProgress.remove(session.electorId());
            publishVoterState(new VotingScreenModel.Waiting("Falha no registro do voto."));
            throw new RejectedException("Falha ao registrar voto: " + describe(e));
        }
    }

    public Optional<String> requestParty(String number) {
        return Optional.ofNullable(candidates.getPartyByNumber(number));
    }

    public Optional<CandidateEntity> requestCandidate(String cargo, String number) {
        return candidates.buscarPorNumero(cargo, number);
    }

    public boolean hasVoted(String electorId) {
        return voted.contains(electorId);
    }

    public synchronized boolean cancelSession(Session session) {
        if (session == null || currentVoter == null || !currentVoter.equals(session.electorId())) return false;
        if (mesarioStep == MesarioStep.CLOSED) return false;

        cancelCurrentSession();
        return true;
    }

    // ==================== PUBLICAÇÃO DE ESTADO (OUTPUT) ====================

    private void publishMesarioState() {
        if (mesarioStateListener == null) return;

        String time = LocalTime.now(clock).format(TIME_FORMAT);
        String counter = String.format(Locale.ROOT, "%04d/%04d", votesCount, totalVoters);

        String prompt = "";
        String line2 = "";
        String right2 = counter;

        switch (mesarioStep) {
            case ENTER_ID -> {
                prompt = "Informe o Título de Eleitor";
                line2 = idMask();
            }

            case CONFIRM_ID -> {
                prompt = "Título: " + formatId(idInput.toString());
                line2 = "CONFIRMA=Liberar  CORRIGE=Refazer";
                right2 = "";
            }

            case AUTHORIZED -> {
                if (released) {
                    prompt = "Urna liberada: " + formatId(currentVoter == null ? "" : currentVoter);
                    line2 = "Aguardando voto (CORRIGE cancela)";
                } else {
                    prompt = "Aguarde: verificando título";
                    line2 = formatId(currentVoter == null ? "" : currentVoter);
                }
                right2 = "";
            }

            case MENU -> {
                prompt = "Outras opções";
                line2 = "1-Voltar  2-Encerrar votação";
            }

            case CLOSE_ID -> {
                prompt = "Título do Presidente da mesa";
                line2 = idMask();
            }

            case CLOSED -> {
                prompt = "VOTAÇÃO ENCERRADA";
                line2 = "Total de votos registrados: " + votesCount;
                right2 = "";
            }
        }

        if (noticeText != null) prompt = noticeText;

        MesarioScreenModel state = new MesarioScreenModel(
                compose(prompt, time),
                compose(line2, right2),
                mesarioStep == MesarioStep.AUTHORIZED && released,
                mesarioStep == MesarioStep.AUTHORIZED && !released,
                mesarioStep != MesarioStep.CLOSED,
                votesCount,
                totalVoters
        );
        mesarioStateListener.accept(state);
    }

    public void publishVoterState(VotingScreenModel model) {
        if (voterScreenListener != null) {
            voterScreenListener.accept(model);
        }
    }

    // ==================== UTILITÁRIOS ====================

    private void setNotice(String text) { this.noticeText = text; }
    private void clearNotice() { this.noticeText = null; }

    private String idMask() {
        StringBuilder sb = new StringBuilder(idInput);
        while (sb.length() < ID_LENGTH) sb.append('_');
        return sb.toString();
    }

    private static String formatId(String id) {
        if (id == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < id.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(id.charAt(i));
        }
        return sb.toString();
    }

    private static String compose(String left, String right) {
        int max = 40 - right.length() - 1;
        String l = left.length() > max ? left.substring(0, Math.max(0, max)) : left;
        StringBuilder sb = new StringBuilder(l);
        while (sb.length() < 40 - right.length()) sb.append(' ');
        return sb.append(right).toString();
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    public void setTotalVoters(int total) {
        this.totalVoters = Math.max(0, total);
        publishMesarioState();
    }

    public void setPresidentId(String id) throws Exception {
        if (!electors.isElectorIdValid(id)) throw new Exception("Titulo eleitoral do presidente é inválido!");
        this.presidentId = id.replaceAll("\\D", "");
    }

    public void setClock(Clock clock) {
        this.clock = clock;
        publishMesarioState();
    }
}
