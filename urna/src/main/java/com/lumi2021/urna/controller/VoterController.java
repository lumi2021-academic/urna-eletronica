package com.lumi2021.urna.controller;

import com.lumi2021.urna.application.VotingServer;
import com.lumi2021.urna.audio.SoundEffects;
import com.lumi2021.urna.audio.UrnaSound;
import com.lumi2021.urna.domain.models.CandidateEntity;
import com.lumi2021.urna.infra.firebase.VotoRequest;
import com.lumi2021.urna.view.models.VotingScreenModel;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class VoterController {

    public static final Duration DEFAULT_CONFIRM_DELAY = Duration.ofSeconds(1);

    private static final ScheduledExecutorService UNLOCK_TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "confirm-unlock");
        t.setDaemon(true);
        return t;
    });

    private final VotingServer server;
    private final Duration confirmDelay;
    private volatile SoundEffects sounds = SoundEffects.NONE;
    private long lockedUntilNanos = 0;
    private boolean confirmableShown = false;
    private String currentCargo = VotingServer.VEREADOR;
    private final StringBuilder digits = new StringBuilder();

    private VotoRequest vereadorVote;

    private CandidateEntity currentCandidate;
    private boolean isBlankVote = false;

    public VoterController(VotingServer server) {
        this(server, DEFAULT_CONFIRM_DELAY);
    }

    public VoterController(VotingServer server, Duration confirmDelay) {
        this.server = server;
        this.confirmDelay = confirmDelay;
    }

    public void setSounds(SoundEffects sounds) {
        this.sounds = sounds == null ? SoundEffects.NONE : sounds;
    }

    public void setDisplay(Consumer<VotingScreenModel> listener) {
        server.setVoterResetListener(this::reset);
        server.setVoterScreenListener(model -> {
            if (model instanceof VotingScreenModel.Finished) sounds.play(UrnaSound.FIM);
            listener.accept(model);
        });
    }

    public void reset() {
        synchronized (server) {
            currentCargo = VotingServer.VEREADOR;
            vereadorVote = null;
            resetCargoState();
        }
    }

    public void digit(int digit) {
        synchronized (server) {
            if (!server.isVoterActive()) return;

            if (digits.length() < getTargetDigitCount() && !isBlankVote) {
                sounds.play(UrnaSound.TECLA);
                digits.append(digit);
                evaluateInput();
            }
        }
    }

    public void blank() {
        synchronized (server) {
            if (!server.isVoterActive()) return;

            if (digits.isEmpty()) {
                sounds.play(UrnaSound.TECLA);
                isBlankVote = true;
                currentCandidate = null;
                publishCurrentState();
            }
        }
    }

    public void correct() {
        synchronized (server) {
            if (!server.isVoterActive()) return;

            if (!digits.isEmpty()) {
                sounds.play(UrnaSound.TECLA);
                digits.setLength(0);
                currentCandidate = null;
                isBlankVote = false;
                publishCurrentState();
            }
            else if (isBlankVote) {
                sounds.play(UrnaSound.TECLA);
                isBlankVote = false;
                publishCurrentState();
            }
        }
    }

    public void confirm() {
        synchronized (server) {
            if (!server.isVoterActive()) return;

            boolean complete = digits.length() == getTargetDigitCount();
            if (!(isBlankVote || currentCandidate != null || complete)) return;
            if (System.nanoTime() < lockedUntilNanos) return;

            sounds.play(UrnaSound.CONFIRMA);
            VotoRequest request = buildVotoRequest();

            if (VotingServer.VEREADOR.equals(currentCargo)) {
                vereadorVote = request;
                currentCargo = VotingServer.PREFEITO;
                resetCargoState();
                publishCurrentState();
            }
            else server.submitVote(vereadorVote, request);
        }
    }

    private int getTargetDigitCount() {
        return VotingServer.VEREADOR.equals(currentCargo) ? 5 : 2;
    }

    private void resetCargoState() {
        digits.setLength(0);
        currentCandidate = null;
        isBlankVote = false;
        lockedUntilNanos = 0;
        confirmableShown = false;
    }

    private void evaluateInput() {
        if (digits.length() == getTargetDigitCount()) {
            Optional<CandidateEntity> candidateOpt = server.requestCandidate(currentCargo, digits.toString());
            currentCandidate = candidateOpt.orElse(null);
        }
        else currentCandidate = null;
        publishCurrentState();
    }

    private VotoRequest buildVotoRequest() {
        if (isBlankVote) return new VotoRequest(currentCargo, "BRANCO", null);
        if (currentCandidate != null) return new VotoRequest(currentCargo, "VALIDO", currentCandidate.numero());

        return new VotoRequest(currentCargo, "NULO", null);
    }

    private void publishCurrentState() {
        int digitCount = getTargetDigitCount();

        boolean confirmable = isBlankVote || currentCandidate != null || digits.length() == digitCount;
        if (confirmable && !confirmableShown && !confirmDelay.isZero()) {
            lockedUntilNanos = System.nanoTime() + confirmDelay.toNanos();
            UNLOCK_TIMER.schedule(() -> {
                synchronized (server) {
                    if (server.isVoterActive() && confirmableShown) publishCurrentState();
                }
            }, confirmDelay.toMillis(), TimeUnit.MILLISECONDS);
        }
        confirmableShown = confirmable;
        boolean locked = confirmable && System.nanoTime() < lockedUntilNanos;

        String headline = null;
        String candidateName = null;
        String partyName = null;
        String imageUrl = null;

        if (isBlankVote) {
            headline = "VOTO EM BRANCO";
        }

        else if (currentCandidate != null) {
            candidateName = currentCandidate.nome();
            partyName = currentCandidate.partido();
            imageUrl = currentCandidate.imagemUrl();
        }

        else if (digits.length() == digitCount) {
            headline = "VOTO NULO";
        }

        server.publishVoterState(new VotingScreenModel.Voting(
                currentCargo,
                digitCount,
                digits.toString(),
                candidateName,
                partyName,
                imageUrl,
                headline,
                null,
                VotingServer.VEREADOR.equals(currentCargo) ? 0 : 1,
                locked,
                null,
                null
        ));
    }
}
