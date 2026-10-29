package br.com.urnapokemon;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class ConfigurarCandidatos {
    private static final String ELEICAO_PADRAO = "eleicao-001";
    private static final String CSV_PADRAO = "candidatos_exemplo.csv";
    private static final long TIMEOUT = 20;

    private record Linha(String pokemonId, String cargo, String numero) {}
    private record Pendencia(Linha linha, String partidoId) {}

    private ConfigurarCandidatos() {}

    public static void main(String[] args) throws Exception {
        boolean gravar = false;
        String arquivo = CSV_PADRAO;
        for (String arg : args) {
            if ("--gravar".equals(arg)) gravar = true;
            else if (arg.startsWith("--arquivo=")) arquivo = arg.substring("--arquivo=".length());
            else throw new IllegalArgumentException("Uso: configurarCandidatos [--arquivo=arquivo.csv] [--gravar]");
        }
        List<Linha> linhas = lerArquivo(Path.of(arquivo));
        String projeto = obrigatoria("FIREBASE_PROJECT_ID");
        String eleicaoId = System.getenv().getOrDefault("ELEICAO_ID", ELEICAO_PADRAO);
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(projeto)
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(options, "configuracao-candidatos");
        Firestore db = FirestoreClient.getFirestore(app);
        try {
            var eleicao = db.collection("eleicoes").document(eleicaoId);
            var eleicaoAtual = eleicao.get().get(TIMEOUT, TimeUnit.SECONDS);
            if (!eleicaoAtual.exists() || !"MUNICIPAL".equals(eleicaoAtual.getString("modalidade"))
                    || !"CONFIGURACAO".equals(eleicaoAtual.getString("status"))
                    || Boolean.TRUE.equals(eleicaoAtual.getBoolean("ativa"))) {
                throw new IllegalStateException("Eleicao inexistente, nao municipal, ou fora da fase CONFIGURACAO.");
            }

            var candidatosRef = eleicao.collection("candidatos");
            var existentes = candidatosRef.get().get(TIMEOUT, TimeUnit.SECONDS).getDocuments();
            Map<String, DocumentSnapshot> existentesPorPokemon = new HashMap<>();
            Map<String, String> primeiroDocumentoPorNumero = new HashMap<>();
            Set<String> numerosOcupados = new HashSet<>();
            for (DocumentSnapshot doc : existentes) {
                existentesPorPokemon.put(doc.getId(), doc);
                String cargo = doc.getString("cargo");
                String numero = doc.getString("numero");
                if (cargo != null && numero != null) {
                    String chave = cargo + ":" + numero;
                    String primeiroId = primeiroDocumentoPorNumero.putIfAbsent(chave, doc.getId());
                    if (primeiroId != null) {
                        throw new IllegalStateException(
                                "Numero duplicado no banco: " + cargo + " " + numero
                                + ". Documentos: " + primeiroId + " e " + doc.getId());
                    }
                    numerosOcupados.add(chave);
                }
            }
            List<Pendencia> pendentes = new ArrayList<>();
            int preservados = 0;
            for (Linha linha : linhas) {
                DocumentSnapshot pokemon = db.collection("pokemons").document(linha.pokemonId())
                        .get().get(TIMEOUT, TimeUnit.SECONDS);
                if (!pokemon.exists() || !Boolean.TRUE.equals(pokemon.getBoolean("disponivel"))) {
                    throw new IllegalStateException("Pokemon inexistente ou indisponivel: " + linha.pokemonId());
                }
                String partidoId = pokemon.getString("partidoId");
                if (partidoId == null || !partidoId.equals(pokemon.getString("tipoPrimario"))) {
                    throw new IllegalStateException("Partido inconsistente no Pokemon " + linha.pokemonId());
                }
                DocumentSnapshot partido = db.collection("partidos").document(partidoId)
                        .get().get(TIMEOUT, TimeUnit.SECONDS);
                if (!partido.exists() || !Boolean.TRUE.equals(partido.getBoolean("ativo"))) {
                    throw new IllegalStateException("Partido inexistente ou inativo: " + partidoId);
                }
                String numeroPartido = partido.getString("numero");
                if (numeroPartido == null || numeroPartido.length() != 2 ||
                        !(linha.cargo().equals("PREFEITO") ? linha.numero().equals(numeroPartido)
                                : linha.numero().startsWith(numeroPartido))) {
                    throw new IllegalStateException("Numero eleitoral " + linha.numero()
                            + " nao corresponde ao partido " + partidoId);
                }

                DocumentSnapshot atual = existentesPorPokemon.get(linha.pokemonId());
                if (atual != null) {
                    if (!linha.cargo().equals(atual.getString("cargo"))
                            || !linha.numero().equals(atual.getString("numero"))
                            || !partidoId.equals(atual.getString("partidoId"))
                            || !linha.pokemonId().equals(atual.getString("pokemonId"))) {
                        throw new IllegalStateException("Candidato " + linha.pokemonId()
                                + " ja existe com dados diferentes. Revisar manualmente antes de mudar.");
                    }
                    preservados++;
                    continue;
                }
                if (!numerosOcupados.add(linha.cargo() + ":" + linha.numero())) {
                    throw new IllegalStateException("Numero ja utilizado no Firestore: " + linha.numero());
                }
                pendentes.add(new Pendencia(linha, partidoId));
            }

            System.out.printf("Projeto %s, eleicao %s. %d candidatos no CSV: %d existentes, %d novos.%n",
                    projeto, eleicaoId, linhas.size(), preservados, pendentes.size());
            for (Pendencia item : pendentes) {
                System.out.printf("NOVO: Pokemon %s -> %s (%s), partido %s%n",
                        item.linha().pokemonId(), item.linha().cargo(), item.linha().numero(), item.partidoId());
            }
            if (!gravar) {
                System.out.println("SIMULACAO: nenhuma alteracao. Use --gravar somente apos revisar a lista.");
                return;
            }
            if (pendentes.isEmpty()) {
                System.out.println("Nenhum candidato novo a criar.");
                return;
            }
            WriteBatch lote = db.batch();
            for (Pendencia item : pendentes) {
                Linha c = item.linha();
                lote.create(candidatosRef.document(c.pokemonId()), Map.of(
                        "pokemonId", c.pokemonId(), "partidoId", item.partidoId(),
                        "cargo", c.cargo(), "numero", c.numero(),
                        "ativo", true, "votos", 0L));
            }
            lote.commit().get(TIMEOUT, TimeUnit.SECONDS);
            System.out.printf("CADASTRO: %d criados e %d preservados.%n", pendentes.size(), preservados);
        } finally {
            db.close();
            app.delete();
        }
    }

    private static List<Linha> lerArquivo(Path arquivo) throws Exception {
        List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
        if (linhas.isEmpty() || !"pokemonId,cargo,numero".equals(linhas.get(0).strip())) {
            throw new IllegalArgumentException("Cabecalho esperado: pokemonId,cargo,numero");
        }
        var saida = new ArrayList<Linha>();
        Set<String> pokemons = new HashSet<>();
        Set<String> numeros = new HashSet<>();
        for (int i = 1; i < linhas.size(); i++) {
            if (linhas.get(i).isBlank()) continue;
            String[] c = linhas.get(i).trim().split(",", -1);
            if (c.length != 3) throw new IllegalArgumentException("CSV invalido na linha " + (i + 1));
            Linha item = new Linha(c[0].trim(), c[1].trim(), c[2].trim());
            if (!item.pokemonId().matches("[0-9]{3}")
                    || !(item.cargo().equals("VEREADOR") && item.numero().matches("[0-9]{5}")
                      || item.cargo().equals("PREFEITO") && item.numero().matches("[0-9]{2}"))) {
                throw new IllegalArgumentException("Pokemon, cargo ou numero invalido na linha " + (i + 1));
            }
            if (!pokemons.add(item.pokemonId()) || !numeros.add(item.cargo() + ":" + item.numero())) {
                throw new IllegalArgumentException("Pokemon ou numero duplicado na linha " + (i + 1));
            }
            saida.add(item);
        }
        if (saida.isEmpty() || saida.size() > 151) throw new IllegalArgumentException("Informe de 1 a 151 candidatos.");
        return saida;
    }

    private static String obrigatoria(String nome) {
        String v = System.getenv(nome);
        if (v == null || v.isBlank()) throw new IllegalStateException("Defina " + nome);
        return v;
    }
}
