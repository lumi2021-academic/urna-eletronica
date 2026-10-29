package br.com.urnapokemon;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Importa apenas os Pokemon que ainda nao existem no Firestore. */
public final class ImportarPokemons {

    private static final String CABECALHO =
            "id,numeroPokedex,nome,tipoPrimario,tipoSecundario,partidoId,geracao,disponivel,imagemUrl";

    private static final Set<String> PARTIDOS_VALIDOS = Set.of(
            "normal", "lutador", "planta", "fogo", "agua", "eletrico",
            "venenoso", "terrestre", "pedra", "inseto", "fantasma",
            "psiquico", "gelo", "dragao", "fada");

    private ImportarPokemons() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !args[0].equals("--gravar"))) {
            System.err.println("Uso: gradlew :firebase-importador:run [--args=--gravar]");
            System.exit(2);
        }
        final boolean gravar = args.length == 1;

        var dados = lerCsv(Path.of("pokemon_151.csv"));
        System.out.printf("CSV validado: %d Pokemon da primeira geracao.%n", dados.size());

        String projeto = System.getenv("FIREBASE_PROJECT_ID");
        if (projeto == null || projeto.isBlank()) {
            throw new IllegalStateException("Defina FIREBASE_PROJECT_ID com o ID real do projeto Firebase.");
        }
        String credenciais = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credenciais == null || credenciais.isBlank()) {
            throw new IllegalStateException("Defina GOOGLE_APPLICATION_CREDENTIALS com o caminho do JSON da conta de servico.");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(projeto)
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(options);
        Firestore db = FirestoreClient.getFirestore(app);
        try {

            List<DocumentReference> partidoRefs = new ArrayList<>();
            for (String id : PARTIDOS_VALIDOS) {
                partidoRefs.add(db.collection("partidos").document(id));
            }
            for (DocumentSnapshot snapshot : db.getAll(partidoRefs.toArray(new DocumentReference[0])).get()) {
                if (!snapshot.exists()) {
                    throw new IllegalStateException("Falta o partido '" + snapshot.getId()
                            + "' em /partidos. Corrija o cadastro antes da importacao.");
                }
            }

            List<DocumentReference> pokemonRefs = new ArrayList<>();
            for (Map<String, Object> linha : dados) {
                pokemonRefs.add(db.collection("pokemons").document((String) linha.get("id")));
            }
            List<DocumentSnapshot> snapshots = db.getAll(pokemonRefs.toArray(new DocumentReference[0])).get();
            List<Integer> ausentes = new ArrayList<>();
            int existentes = 0;
            for (int i = 0; i < snapshots.size(); i++) {
                if (snapshots.get(i).exists()) {
                    existentes++;
                } else {
                    ausentes.add(i);
                }
            }
            System.out.printf("Projeto: %s | Pokemon ja cadastrados: %d | Ausentes: %d%n",
                    projeto, existentes, ausentes.size());
            if (!gravar) {
                System.out.println("SIMULACAO: nenhum documento foi criado. Para importar, execute com --gravar.");
                return;
            }
            if (ausentes.isEmpty()) {
                System.out.println("Nenhum registro novo a importar.");
                return;
            }

            // O batch utiliza create para nao sobrescrever documentos existentes.
            WriteBatch lote = db.batch();
            for (int indice : ausentes) {
                Map<String, Object> dadosPokemon = new HashMap<>(dados.get(indice));
                dadosPokemon.remove("id");
                lote.create(pokemonRefs.get(indice), dadosPokemon);
            }
            lote.commit().get();
            System.out.printf("Importacao concluida: %d criados, %d preservados.%n",
                    ausentes.size(), existentes);
        } finally {
            db.close();
            app.delete();
        }
    }

    private static List<Map<String, Object>> lerCsv(Path arquivo) throws Exception {
        List<Map<String, Object>> linhas = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> partidosVistos = new LinkedHashSet<>();
        try (BufferedReader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            String cabecalho = leitor.readLine();
            if (!CABECALHO.equals(cabecalho)) {
                throw new IllegalArgumentException("Cabecalho do CSV diferente do esperado: " + cabecalho);
            }
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.isBlank()) continue;
                // O formato deste CSV nao contem virgulas internas nem campos entre aspas.
                String[] c = linha.split(",", -1);
                if (c.length != 9) {
                    throw new IllegalArgumentException("Quantidade incorreta de colunas: " + linha);
                }
                String id = c[0];
                int numero = Integer.parseInt(c[1]);
                if (!id.equals(String.format("%03d", numero)) || numero < 1 || numero > 151) {
                    throw new IllegalArgumentException("ID ou numeroPokedex invalido: " + linha);
                }
                if (!ids.add(id)) {
                    throw new IllegalArgumentException("Pokemon duplicado: " + id);
                }
                String principal = c[3];
                String partido = c[5];
                if (!PARTIDOS_VALIDOS.contains(principal) || !principal.equals(partido)) {
                    throw new IllegalArgumentException("Partido nao corresponde ao tipo primario: " + id);
                }
                if (Integer.parseInt(c[6]) != 1 || !c[7].equals("true")) {
                    throw new IllegalArgumentException("Geracao ou disponibilidade invalida: " + id);
                }
                partidosVistos.add(partido);
                Map<String, Object> registro = new HashMap<>();
                registro.put("id", id);
                registro.put("numeroPokedex", numero);
                registro.put("nome", c[2]);
                registro.put("tipoPrimario", principal);
                registro.put("tipoSecundario", c[4].isEmpty() ? null : c[4]);
                registro.put("partidoId", partido);
                registro.put("geracao", 1);
                registro.put("disponivel", true);
                registro.put("imagemUrl", c[8]);
                linhas.add(registro);
            }
        }
        if (linhas.size() != 151 || !partidosVistos.containsAll(PARTIDOS_VALIDOS)) {
            throw new IllegalArgumentException("O CSV deve conter 151 Pokemon e os 15 tipos primarios.");
        }
        return linhas;
    }
}
