package com.lumi2021.urna.firebase.backend;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class FirestoreLeituras {
    private final Firestore db;
    private final String eleicaoId;

    public FirestoreLeituras(Firestore db, String eleicaoId) {
        this.db = db;
        this.eleicaoId = eleicaoId;
    }

    public Optional<Map<String, Object>> eleicao() throws Exception {
        var snap = db.collection("eleicoes").document(eleicaoId)
                .get().get(15, TimeUnit.SECONDS);
        if (!snap.exists()) return Optional.empty();
        Map<String,Object> saida = new LinkedHashMap<>();
        saida.put("id", snap.getId());
        for (String campo : new String[]{"nome","modalidade","status","ativa","segundoTurno","cargos"}) {
            saida.put(campo, snap.get(campo));
        }
        return Optional.of(saida);
    }

    public Optional<Map<String,Object>> apuracao(String cargo) throws Exception {
        if (!ValidacaoEleitoral.cargoValido(cargo)) {
            throw new IllegalArgumentException("Cargo invalido");
        }
        var snap = db.collection("eleicoes").document(eleicaoId)
                .collection("apuracao").document(cargo)
                .get().get(15, TimeUnit.SECONDS);
        if (!snap.exists()) return Optional.empty();
        Map<String,Object> saida = new LinkedHashMap<>();
        saida.put("cargo", cargo);
        for (String campo : new String[]{"validos", "brancos", "nulos", "total"}) {
            saida.put(campo, snap.getLong(campo));
        }
        return Optional.of(saida);
    }

    public Optional<Map<String,Object>> pokemon(String id) throws Exception {
        if (id == null || !id.matches("[0-9]{3}")) {
            throw new IllegalArgumentException("ID da Pokedex deve ter tres digitos");
        }
        DocumentSnapshot snap = db.collection("pokemons").document(id)
                .get().get(15, TimeUnit.SECONDS);
        if (!snap.exists()) return Optional.empty();
        Map<String,Object> saida = new LinkedHashMap<>();
        saida.put("id", snap.getId());
        for (String campo : new String[]{"numeroPokedex","nome","tipoPrimario","tipoSecundario","partidoId","imagemUrl"}) {
            saida.put(campo, snap.get(campo));
        }
        return Optional.of(saida);
    }
}
