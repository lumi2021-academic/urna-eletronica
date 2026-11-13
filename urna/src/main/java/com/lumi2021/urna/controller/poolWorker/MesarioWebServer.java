package com.lumi2021.urna.controller.poolWorker;

import com.lumi2021.urna.platform.events.InputAction;
import com.lumi2021.urna.view.models.MesarioScreenModel;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.UnauthorizedResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MesarioWebServer implements AutoCloseable {

    private static final Set<InputAction> ALLOWED = EnumSet.of(
            InputAction.KEY_0, InputAction.KEY_1, InputAction.KEY_2, InputAction.KEY_3, InputAction.KEY_4,
            InputAction.KEY_5, InputAction.KEY_6, InputAction.KEY_7, InputAction.KEY_8, InputAction.KEY_9,
            InputAction.CORRIGE, InputAction.CONFIRMA);

    private final PoolWorkerController controller;
    private final String token;
    private final int port;
    private final String page;
    private Javalin app;

    private volatile String bulletinText = "";
    private volatile int bulletinVersion = 0;

    private static final long CONNECTED_WINDOW_MILLIS = 5000;
    private volatile long lastSeenMillis = 0;
    private volatile boolean connected = false;
    private volatile Consumer<Boolean> connectionListener;
    private ScheduledExecutorService watcher;

    private volatile MesarioScreenModel state = new MesarioScreenModel(
            "", "", false, false, true, 0, 0);

    public MesarioWebServer(PoolWorkerController controller, int port, String token) {
        this.controller = controller;
        this.port = port;
        this.token = token;
        this.page = loadPage();
    }

    public void start() {
        controller.setViewStateListener(s -> this.state = s);

        app = Javalin.create(config -> config.showJavalinBanner = false);

        app.get("/", ctx -> ctx.html(page));

        app.before("/api/*", ctx -> {
            String given = ctx.queryParam("token");
            if (given == null || !MessageDigest.isEqual(
                    given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
                throw new UnauthorizedResponse();
            }
            lastSeenMillis = System.currentTimeMillis();
        });

        app.get("/api/state", ctx -> ctx.contentType("application/json; charset=utf-8")
                .result(toJson(state, bulletinVersion)));

        app.get("/api/bulletin", ctx -> ctx.contentType("text/plain; charset=utf-8")
                .result(bulletinText));

        app.post("/api/input/{key}", ctx -> {
            InputAction action;
            try {
                action = InputAction.valueOf(ctx.pathParam("key"));
            }
            catch (IllegalArgumentException e) {
                throw new BadRequestResponse("Tecla inválida");
            }
            if (!ALLOWED.contains(action)) throw new BadRequestResponse("Tecla inválida");

            controller.handleInput(action);
            ctx.status(204);
        });

        app.start(port);

        watcher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mesario-connection-watch");
            t.setDaemon(true);
            return t;
        });
        watcher.scheduleWithFixedDelay(this::checkConnection, 500, 500, TimeUnit.MILLISECONDS);
    }

    public void setConnectionListener(Consumer<Boolean> listener) {
        this.connectionListener = listener;
    }

    public boolean isConnected() { return connected; }

    private void checkConnection() {
        long seen = lastSeenMillis;
        boolean now = seen != 0 && System.currentTimeMillis() - seen < CONNECTED_WINDOW_MILLIS;
        if (now == connected) return;

        connected = now;
        Consumer<Boolean> listener = connectionListener;
        if (listener != null) {
            try {
                listener.accept(now);
            }
            catch (RuntimeException ignored) { }
        }
    }

    public synchronized void publishBulletin(String text) {
        bulletinText = text == null ? "" : text;
        bulletinVersion++;
    }

    public int port() { return port; }
    public String token() { return token; }

    @Override
    public void close() {
        if (watcher != null) watcher.shutdownNow();
        if (app != null) app.stop();
    }

    static String toJson(MesarioScreenModel s, int bulletinVersion) {
        return "{\"line1\":" + quote(s.lcdLine1())
                + ",\"line2\":" + quote(s.lcdLine2())
                + ",\"liberado\":" + s.ledLiberadoOn()
                + ",\"aguarde\":" + s.ledAguardeOn()
                + ",\"teclado\":" + s.tecladoAtivo()
                + ",\"votos\":" + s.votesCount()
                + ",\"total\":" + s.totalVoters()
                + ",\"boletim\":" + bulletinVersion + "}";
    }

    private static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (text == null ? "" : text).toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String loadPage() {
        try (InputStream in = MesarioWebServer.class.getResourceAsStream("/web/mesario.html")) {
            if (in == null) throw new IllegalStateException("Recurso /web/mesario.html não encontrado");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("Falha ao ler /web/mesario.html", e);
        }
    }
}
