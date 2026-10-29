package com.lumi2021.urna.firebase.backend;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Cadastro administrativo restrito ao backend. */
public final class CadastroCandidatosService {
    private final Firestore db;
    private final String eleicaoId;

    public CadastroCandidatosService(Firestore db, String eleicaoId) {
        this.db = db;
        this.eleicaoId = eleicaoId;
    }

    public record ResultadoCadastro(String status, String pokemonId, String cargo,
                                    String numero, String partidoId) { }

    public static final class CadastroException extends RuntimeException {
        private final int httpStatus;
        public CadastroException(int httpStatus, String mensagem) {
            super(mensagem);
            this.httpStatus = httpStatus;
        }
        public int httpStatus() { return httpStatus; }
    }

    public ResultadoCadastro cadastrar(CandidaturaRequest requisicao) throws Exception {
        if (requisicao == null || requisicao.pokemonId() == null ||
                !requisicao.pokemonId().matches("[0-9]{3}") ||
                !ValidacaoEleitoral.numeroValido(requisicao.cargo(), requisicao.numero())) {
            throw new CadastroException(400, "Informe pokemonId, cargo e numero validos");
        }

        DocumentReference eleicaoRef = db.collection("eleicoes").document(eleicaoId);
        CollectionReference candidatosRef = eleicaoRef.collection("candidatos");
        DocumentReference candidatoRef = candidatosRef.document(requisicao.pokemonId());
        DocumentReference pokemonRef = db.collection("pokemons").document(requisicao.pokemonId());
        // Reserva o numero por cargo para evitar cadastros concorrentes.
        DocumentReference numeroRef = eleicaoRef.collection("numerosCandidatos")
                .document(requisicao.cargo() + "_" + requisicao.numero());

        try {
            return db.runTransaction(tx -> {
                // O Firestore exige que as leituras da transacao precedam as escritas.
                DocumentSnapshot eleicao = tx.get(eleicaoRef).get();
                DocumentSnapshot pokemon = tx.get(pokemonRef).get();
                DocumentSnapshot existente = tx.get(candidatoRef).get();
                DocumentSnapshot reserva = tx.get(numeroRef).get();
                QuerySnapshot numerosExistentes = tx.get(candidatosRef
                        .whereEqualTo("cargo", requisicao.cargo())
                        .whereEqualTo("numero", requisicao.numero())
                        .limit(2)).get();

                if (!eleicao.exists() || !"MUNICIPAL".equals(eleicao.getString("modalidade"))) {
                    throw new CadastroException(409, "Eleicao municipal nao encontrada");
                }
                if (!"CONFIGURACAO".equals(eleicao.getString("status")) ||
                        Boolean.TRUE.equals(eleicao.getBoolean("ativa"))) {
                    throw new CadastroException(409, "Eleicao fora da fase de configuracao");
                }
                if (!pokemon.exists() || !Boolean.TRUE.equals(pokemon.getBoolean("disponivel"))) {
                    throw new CadastroException(404, "Pokemon inexistente ou indisponivel");
                }
                if (existente.exists()) {
                    throw new CadastroException(409, "Pokemon ja cadastrado como candidato nesta eleicao");
                }
                if (reserva.exists() || !numerosExistentes.isEmpty()) {
                    throw new CadastroException(409, "Numero eleitoral ja ocupado neste cargo");
                }

                String partidoId = pokemon.getString("partidoId");
                if (partidoId == null || !partidoId.equals(pokemon.getString("tipoPrimario"))) {
                    throw new CadastroException(409, "Partido do Pokemon inconsistente");
                }
                DocumentReference partidoRef = db.collection("partidos").document(partidoId);
                DocumentSnapshot partido = tx.get(partidoRef).get();
                if (!partido.exists() || !Boolean.TRUE.equals(partido.getBoolean("ativo"))) {
                    throw new CadastroException(409, "Partido inexistente ou inativo");
                }
                String numeroPartido = partido.getString("numero");
                if (!ValidacaoEleitoral.numeroDoPartido(
                        requisicao.cargo(), requisicao.numero(), numeroPartido)) {
                    throw new CadastroException(400, "Numero eleitoral nao corresponde ao partido");
                }

                Map<String, Object> dados = new HashMap<>();
                dados.put("pokemonId", requisicao.pokemonId());
                dados.put("partidoId", partidoId);
                dados.put("cargo", requisicao.cargo());
                dados.put("numero", requisicao.numero());
                dados.put("votos", 0L);
                dados.put("ativo", true);
                dados.put("criadoEm", FieldValue.serverTimestamp());

                tx.create(candidatoRef, dados);
                tx.create(numeroRef, Map.of(
                        "pokemonId", requisicao.pokemonId(),
                        "cargo", requisicao.cargo(),
                        "numero", requisicao.numero()));

                return new ResultadoCadastro("criado", requisicao.pokemonId(),
                        requisicao.cargo(), requisicao.numero(), partidoId);
            }).get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable causa = e;
            while (causa != null) {
                if (causa instanceof CadastroException erro) throw erro;
                causa = causa.getCause();
            }
            throw e;
        }
    }
}
