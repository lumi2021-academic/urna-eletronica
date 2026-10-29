package com.lumi2021.urna.firebase.backend;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class FirestoreCandidateRepository implements CandidateRepository {
    private final Firestore db;
    private final String eleicaoId;
    public FirestoreCandidateRepository(Firestore db, String eleicaoId) {
        this.db = db;
        this.eleicaoId = eleicaoId;
    }

    @Override
    public Optional<CandidateInfo> buscarPorNumero(String cargo, String numero) throws Exception {
        if (!ValidacaoEleitoral.numeroValido(cargo, numero)) {
            throw new IllegalArgumentException("Cargo ou numero eleitoral invalido");
        }
        var consulta = db.collection("eleicoes").document(eleicaoId)
                .collection("candidatos")
                .whereEqualTo("cargo", cargo)
                .whereEqualTo("numero", numero)
                .limit(2)
                .get().get(15, TimeUnit.SECONDS);
        if (consulta.isEmpty()) return Optional.empty();
        if (consulta.size() > 1) throw new IllegalStateException("Numero duplicado para o mesmo cargo");
        return resolver(consulta.getDocuments().get(0));
    }

    @Override
    public List<CandidateInfo> listarPorCargo(String cargo) throws Exception {
        if (!ValidacaoEleitoral.cargoValido(cargo)) throw new IllegalArgumentException("Cargo invalido");
        var consulta = db.collection("eleicoes").document(eleicaoId)
                .collection("candidatos")
                .whereEqualTo("cargo", cargo)
                .get().get(15, TimeUnit.SECONDS);
        var resposta = new ArrayList<CandidateInfo>();
        for (var doc : consulta.getDocuments()) {
            resolver(doc).ifPresent(resposta::add);
        }
        resposta.sort(Comparator.comparing(CandidateInfo::numero));
        return resposta;
    }

    private Optional<CandidateInfo> resolver(DocumentSnapshot candidato) throws Exception {
        if (!Boolean.TRUE.equals(candidato.getBoolean("ativo"))) return Optional.empty();
        String pokemonId = exigir(candidato.getString("pokemonId"), "pokemonId");
        String partidoId = exigir(candidato.getString("partidoId"), "partidoId");
        var pokemon = db.collection("pokemons").document(pokemonId)
                .get().get(15, TimeUnit.SECONDS);
        var partido = db.collection("partidos").document(partidoId)
                .get().get(15, TimeUnit.SECONDS);
        if (!pokemon.exists() || !partido.exists()) {
            throw new IllegalStateException("Pokemon ou partido ausente para candidato " + candidato.getId());
        }
        if (!partidoId.equals(pokemon.getString("partidoId"))) {
            throw new IllegalStateException("Partido do candidato diverge do Pokemon");
        }
        return Optional.of(new CandidateInfo(
                exigir(candidato.getString("numero"), "numero"),
                exigir(candidato.getString("cargo"), "cargo"),
                pokemonId,
                exigir(pokemon.getString("nome"), "nome"),
                pokemon.getString("imagemUrl"),
                partidoId,
                exigir(partido.getString("nome"), "partido.nome"),
                exigir(partido.getString("sigla"), "partido.sigla")
        ));
    }
    private static String exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) throw new IllegalStateException("Campo ausente: " + campo);
        return valor;
    }
}
