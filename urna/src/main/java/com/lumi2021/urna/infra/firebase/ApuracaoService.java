package com.lumi2021.urna.infra.firebase;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.lumi2021.urna.domain.models.ApuracaoResult;
import com.lumi2021.urna.domain.models.ApuracaoResult.CargoResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Lê do Firestore os contadores gravados por {@link RegistroVotacaoService}. */
public final class ApuracaoService {
    private static final long TIMEOUT_SECONDS = 30;

    private final Firestore db;
    private final String eleicaoId;

    public ApuracaoService(Firestore db, String eleicaoId) {
        this.db = db;
        this.eleicaoId = eleicaoId;
    }

    public ApuracaoResult carregar() throws Exception {
        DocumentReference eleicao = db.collection("eleicoes").document(eleicaoId);
        List<QueryDocumentSnapshot> candidatos = eleicao.collection("candidatos")
                .get().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getDocuments();

        Map<String, CargoResult> porCargo = new LinkedHashMap<>();
        for (String cargo : new String[]{"PREFEITO", "VEREADOR"}) {
            DocumentSnapshot apuracao = eleicao.collection("apuracao").document(cargo)
                    .get().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Map<String, Long> votos = new LinkedHashMap<>();
            for (QueryDocumentSnapshot doc : candidatos) {
                String numero = doc.getString("numero");
                if (cargo.equals(doc.getString("cargo")) && numero != null) {
                    votos.put(numero, valor(doc.getLong("votos")));
                }
            }

            porCargo.put(cargo, new CargoResult(
                    valor(apuracao.getLong("validos")),
                    valor(apuracao.getLong("brancos")),
                    valor(apuracao.getLong("nulos")),
                    valor(apuracao.getLong("total")),
                    votos));
        }
        return new ApuracaoResult(porCargo);
    }

    private static long valor(Long v) {
        return v == null ? 0L : v;
    }
}
