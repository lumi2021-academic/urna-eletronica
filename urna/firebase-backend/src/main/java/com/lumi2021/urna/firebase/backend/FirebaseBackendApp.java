package com.lumi2021.urna.firebase.backend;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class FirebaseBackendApp {
    private static final Logger log = LoggerFactory.getLogger(FirebaseBackendApp.class);
    private FirebaseBackendApp() { }

    public static void main(String[] args) throws Exception {
        String projeto = obrigatoria("FIREBASE_PROJECT_ID");
        String eleicaoId = System.getenv().getOrDefault("ELEICAO_ID", "eleicao-001");
        String host = System.getenv().getOrDefault("URN_BACKEND_HOST", "127.0.0.1");
        int porta = Integer.parseInt(System.getenv().getOrDefault("URN_BACKEND_PORT", "7070"));
        if (!"127.0.0.1".equals(host) && !"localhost".equals(host)) {
            throw new IllegalArgumentException("Backend apenas local nesta etapa (127.0.0.1)");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(projeto)
                .build();
        FirebaseApp firebaseApp = FirebaseApp.initializeApp(options, "urna-backend");
        Firestore db = FirestoreClient.getFirestore(firebaseApp);
        CandidateRepository candidatos = new FirestoreCandidateRepository(db, eleicaoId);
        FirestoreLeituras leituras = new FirestoreLeituras(db, eleicaoId);
        CadastroCandidatosService cadastro = new CadastroCandidatosService(db, eleicaoId);
        RegistroVotacaoService votacao = new RegistroVotacaoService(db, eleicaoId);
        String tokenAdmin = System.getenv("URN_ADMIN_TOKEN");
        String tokenVotacao = System.getenv("URN_VOTING_TOKEN");
        if (tokenVotacao != null && !tokenVotacao.isBlank() && tokenVotacao.length() < 32) {
            throw new IllegalStateException("URN_VOTING_TOKEN deve ter ao menos 32 caracteres");
        }
        if (tokenVotacao != null && tokenVotacao.equals(tokenAdmin)) {
            throw new IllegalStateException("Use tokens distintos para mesario e urna");
        }
        if (tokenAdmin != null && !tokenAdmin.isBlank() && tokenAdmin.length() < 32) {
            throw new IllegalStateException("URN_ADMIN_TOKEN deve ter ao menos 32 caracteres");
        }

        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok", "servico", "urna-firebase")));

        app.get("/api/candidatos", ctx -> {
            try {
                String cargo = ctx.queryParam("cargo");
                String numero = ctx.queryParam("numero");
                if (!ValidacaoEleitoral.numeroValido(cargo, numero)) {
                    ctx.status(400).json(Map.of("erro", "Informe cargo e numero validos"));
                    return;
                }
                var resultado = candidatos.buscarPorNumero(cargo, numero);
                if (resultado.isPresent()) ctx.json(resultado.get());
                else ctx.status(404).json(Map.of("erro", "Candidato nao encontrado"));
            } catch (Exception e) {
                erroInterno(ctx.path(), e);
                ctx.status(500).json(Map.of("erro", "Falha ao consultar candidato"));
            }
        });
        app.get("/api/candidatos/cargo/{cargo}", ctx -> {
            String cargo = ctx.pathParam("cargo");
            if (!ValidacaoEleitoral.cargoValido(cargo)) {
                ctx.status(400).json(Map.of("erro", "Cargo invalido"));
                return;
            }
            try {
                ctx.json(candidatos.listarPorCargo(cargo));
            } catch (Exception e) {
                erroInterno(ctx.path(), e);
                ctx.status(500).json(Map.of("erro", "Falha ao listar candidatos"));
            }
        });
        app.get("/api/eleicao", ctx -> {
            try {
                responder(ctx, leituras.eleicao());
            } catch (Exception e) {
                erroInterno(ctx.path(), e);
                ctx.status(500).json(Map.of("erro", "Falha ao consultar eleicao"));
            }
        });
        app.get("/api/apuracao", ctx -> {
            String cargo = ctx.queryParam("cargo");
            if (!ValidacaoEleitoral.cargoValido(cargo)) {
                ctx.status(400).json(Map.of("erro", "Cargo invalido"));
                return;
            }
            try {
                responder(ctx, leituras.apuracao(cargo));
            } catch (Exception e) {
                erroInterno(ctx.path(), e);
                ctx.status(500).json(Map.of("erro", "Falha ao consultar apuracao"));
            }
        });
        app.get("/api/pokemons/{id}", ctx -> {
            String id = ctx.pathParam("id");
            if (!id.matches("[0-9]{3}")) {
                ctx.status(400).json(Map.of("erro", "ID invalido"));
                return;
            }
            try {
                responder(ctx, leituras.pokemon(id));
            } catch (Exception e) {
                erroInterno(ctx.path(), e);
                ctx.status(500).json(Map.of("erro", "Falha ao consultar Pokemon"));
            }
        });
        app.post("/api/admin/candidatos", ctx -> {
            if (tokenAdmin == null || tokenAdmin.isBlank()) {
                ctx.status(503).json(Map.of("erro", "Cadastro administrativo desabilitado"));
                return;
            }
            String recebido = ctx.header("X-Admin-Token");
            if (!tokenIgual(tokenAdmin, recebido)) {
                ctx.status(401).json(Map.of("erro", "Nao autorizado"));
                return;
            }
            if (!ctx.isJson()) {
                ctx.status(415).json(Map.of("erro", "Envie application/json"));
                return;
            }
            CandidaturaRequest dados;
            try {
                dados = ctx.bodyAsClass(CandidaturaRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(Map.of("erro", "JSON invalido"));
                return;
            }
            try {
                var resultado = cadastro.cadastrar(dados);
                ctx.status(201).json(resultado);
            } catch (CadastroCandidatosService.CadastroException e) {
                ctx.status(e.httpStatus()).json(Map.of("erro", e.getMessage()));
            } catch (Exception e) {
                erroInterno(ctx.path(), e);
                ctx.status(500).json(Map.of("erro", "Falha ao cadastrar candidato"));
            }
        });

        app.post("/api/admin/sessoes", ctx -> {
            if (tokenAdmin == null || tokenAdmin.isBlank()) {
                ctx.status(503).json(Map.of("erro", "Operacao administrativa desabilitada"));
                return;
            }
            if (!tokenIgual(tokenAdmin, ctx.header("X-Admin-Token"))) {
                ctx.status(401).json(Map.of("erro", "Nao autorizado"));
                return;
            }
            try {
                ctx.status(201).json(votacao.abrirSessao());
            } catch (ValidacaoVotacao.VotacaoException e) {
                ctx.status(e.httpStatus()).json(Map.of("erro", e.getMessage()));
            } catch (Exception e) {
                erroInterno(ctx.path(), e);
                ctx.status(500).json(Map.of("erro", "Falha ao criar sessao"));
            }
        });

        // Nao distribuir o token de votacao no cliente Swing.
        app.post("/api/votacoes", ctx -> {
            if (tokenVotacao == null || tokenVotacao.isBlank()) {
                ctx.status(503).json(Map.of("erro", "Registro de votos desabilitado"));
                return;
            }
            if (!tokenIgual(tokenVotacao, ctx.header("X-Voting-Token"))) {
                ctx.status(401).json(Map.of("erro", "Nao autorizado"));
                return;
            }
            if (!ctx.isJson()) {
                ctx.status(415).json(Map.of("erro", "Envie application/json"));
                return;
            }
            VotacaoRequest request;
            try {
                request = ctx.bodyAsClass(VotacaoRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(Map.of("erro", "JSON invalido"));
                return;
            }
            try {
                ctx.status(201).json(votacao.registrar(request));
            } catch (ValidacaoVotacao.VotacaoException e) {
                ctx.status(e.httpStatus()).json(Map.of("erro", e.getMessage()));
            } catch (Exception e) {
                erroInterno(ctx.path(), e);
                ctx.status(500).json(Map.of("erro", "Falha ao registrar votacao"));
            }
        });

        app.start(host, porta);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            try { db.close(); } catch (Exception e) { log.warn("Erro ao fechar Firestore", e); }
            firebaseApp.delete();
        }));
        log.info("API local em http://{}:{} (projeto {}, eleicao {})", host, porta, projeto, eleicaoId);
    }

    private static boolean tokenIgual(String esperado, String recebido) {
        if (recebido == null) return false;
        // Compara tokens sem interromper a verificacao no primeiro byte diferente.
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                recebido.getBytes(StandardCharsets.UTF_8));
    }

    private static void responder(io.javalin.http.Context ctx, Optional<?> valor) {
        if (valor.isPresent()) ctx.json(valor.get());
        else ctx.status(404).json(Map.of("erro", "Documento nao encontrado"));
    }
    private static String obrigatoria(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) throw new IllegalStateException("Defina a variavel " + nome);
        return valor;
    }
    private static void erroInterno(String rota, Exception e) {
        log.error("Erro na rota {}", rota, e);
    }
}
