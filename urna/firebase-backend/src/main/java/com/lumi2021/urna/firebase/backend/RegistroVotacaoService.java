package com.lumi2021.urna.firebase.backend;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.lumi2021.urna.firebase.backend.ValidacaoVotacao.VotacaoException;

/** Registra votos sem associar as escolhas a identidade do eleitor. */
public final class RegistroVotacaoService {
    private final Firestore db;
    private final String eleicaoId;

    public RegistroVotacaoService(Firestore db, String eleicaoId) {
        this.db = db;
        this.eleicaoId = eleicaoId;
    }

    public record SessaoCriada(String sessaoId, String status, String expiraEm) { }
    public record ConfirmacaoVotacao(String sessaoId, String status) { }

    public SessaoCriada abrirSessao() throws Exception {
        DocumentReference eleicaoRef = db.collection("eleicoes").document(eleicaoId);
        String id = UUID.randomUUID().toString();
        DocumentReference sessaoRef = eleicaoRef.collection("votacoes").document(id);
        Instant limite = Instant.now().plus(10, ChronoUnit.MINUTES);
        Timestamp expira = Timestamp.of(Date.from(limite));
        try {
            return db.runTransaction(tx -> {
                DocumentSnapshot eleicao = tx.get(eleicaoRef).get();
                exigirEleicaoAberta(eleicao);
                tx.create(sessaoRef, Map.of(
                    "status", "PENDENTE",
                    "criadoEm", FieldValue.serverTimestamp(),
                    "expiraEm", expira));
                return new SessaoCriada(id, "PENDENTE", limite.toString());
            }).get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw traduzir(e);
        }
    }

    public ConfirmacaoVotacao registrar(VotacaoRequest request) throws Exception {
        Map<String, VotoRequest> votos = ValidacaoVotacao.validar(request);
        DocumentReference eleicaoRef = db.collection("eleicoes").document(eleicaoId);
        DocumentReference sessaoRef = eleicaoRef.collection("votacoes")
                .document(request.sessaoId());
        CollectionReference candidatos = eleicaoRef.collection("candidatos");
        DocumentReference apuracaoVereador = eleicaoRef.collection("apuracao").document("VEREADOR");
        DocumentReference apuracaoPrefeito = eleicaoRef.collection("apuracao").document("PREFEITO");
        try {
            return db.runTransaction(tx -> {
                // O Firestore exige que as leituras da transacao precedam as escritas.
                DocumentSnapshot eleicao = tx.get(eleicaoRef).get();
                DocumentSnapshot sessao = tx.get(sessaoRef).get();
                DocumentSnapshot apVereador = tx.get(apuracaoVereador).get();
                DocumentSnapshot apPrefeito = tx.get(apuracaoPrefeito).get();
                exigirEleicaoAberta(eleicao);
                if (!sessao.exists()) throw new VotacaoException(404, "Sessao inexistente");
                if (!"PENDENTE".equals(sessao.getString("status"))) {
                    throw new VotacaoException(409, "Sessao ja concluida ou cancelada");
                }
                Timestamp limite = sessao.getTimestamp("expiraEm");
                if (limite == null || !limite.toDate().after(new Date())) {
                    throw new VotacaoException(409, "Sessao expirada");
                }
                if (!apVereador.exists() || !apPrefeito.exists()) {
                    throw new VotacaoException(409, "Apuracao nao inicializada para os dois cargos");
                }
                validarContadores(apVereador);
                validarContadores(apPrefeito);

                Map<String, DocumentReference> candidatosValidos = new HashMap<>();
                for (String cargo : new String[]{"VEREADOR", "PREFEITO"}) {
                    VotoRequest voto = votos.get(cargo);
                    if (!"VALIDO".equals(voto.tipo())) continue;
                    QuerySnapshot encontrados = tx.get(candidatos
                            .whereEqualTo("cargo", cargo)
                            .whereEqualTo("numero", voto.numero()).limit(2)).get();
                    if (encontrados.isEmpty()) {
                        throw new VotacaoException(422, "Candidato nao encontrado para " + cargo);
                    }
                    if (encontrados.size() != 1) {
                        throw new VotacaoException(409, "Numero duplicado no cadastro de " + cargo);
                    }
                    DocumentSnapshot candidato = encontrados.getDocuments().get(0);
                    if (!Boolean.TRUE.equals(candidato.getBoolean("ativo")) ||
                            !cargo.equals(candidato.getString("cargo")) ||
                            !voto.numero().equals(candidato.getString("numero")) ||
                            candidato.getString("pokemonId") == null ||
                            candidato.getLong("votos") == null) {
                        throw new VotacaoException(409, "Candidato desativado ou registro inconsistente");
                    }
                    candidatosValidos.put(cargo, candidato.getReference());
                }

                for (String cargo : new String[]{"VEREADOR", "PREFEITO"}) {
                    VotoRequest voto = votos.get(cargo);
                    DocumentReference apuracaoRef = "VEREADOR".equals(cargo)
                            ? apuracaoVereador : apuracaoPrefeito;
                    String contador = switch (voto.tipo()) {
                        case "VALIDO" -> "validos";
                        case "BRANCO" -> "brancos";
                        case "NULO" -> "nulos";
                        default -> throw new IllegalStateException("Tipo inesperado");
                    };
                    tx.update(apuracaoRef, Map.of(
                            contador, FieldValue.increment(1L),
                            "total", FieldValue.increment(1L)));
                    if ("VALIDO".equals(voto.tipo())) {
                        tx.update(candidatosValidos.get(cargo), "votos", FieldValue.increment(1L));
                    }
                }
                // A sessao armazena apenas o estado do processamento.
                tx.update(sessaoRef, Map.of(
                        "status", "CONCLUIDA",
                        "concluidaEm", FieldValue.serverTimestamp()));
                return new ConfirmacaoVotacao(request.sessaoId(), "CONCLUIDA");
            }).get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw traduzir(e);
        }
    }

    private static void exigirEleicaoAberta(DocumentSnapshot eleicao) {
        if (!eleicao.exists() || !"MUNICIPAL".equals(eleicao.getString("modalidade")) ||
                !"ABERTA".equals(eleicao.getString("status")) ||
                !Boolean.TRUE.equals(eleicao.getBoolean("ativa"))) {
            throw new VotacaoException(409, "Eleicao nao esta aberta");
        }
    }

    private static void validarContadores(DocumentSnapshot doc) {
        for (String campo : new String[]{"validos", "brancos", "nulos", "total"}) {
            Long valor = doc.getLong(campo);
            if (valor == null || valor < 0L) {
                throw new VotacaoException(409, "Contador invalido: " + doc.getId() + "/" + campo);
            }
        }
    }

    private static Exception traduzir(ExecutionException e) {
        Throwable causa = e;
        while (causa != null) {
            if (causa instanceof VotacaoException v) return v;
            causa = causa.getCause();
        }
        return e;
    }
}
