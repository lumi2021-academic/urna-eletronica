package com.lumi2021.urna.platform.boot;

import com.lumi2021.urna.application.UrnaContext;
import com.lumi2021.urna.controller.VoterController;
import com.lumi2021.urna.controller.poolWorker.EmbeddedPoolWorkerController;
import com.lumi2021.urna.controller.poolWorker.MesarioWebServer;
import com.lumi2021.urna.platform.EmbeddedInputService;
import com.lumi2021.urna.platform.NetworkUtils;
import com.lumi2021.urna.view.VotingScreenEmbeddedContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.net.Inet4Address;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class EmbeddedBootstrap implements UrnaBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedBootstrap.class);

    @Override
    public void start(UrnaContext context) {
        var votingServer = context.votingServer();
        var inputService = new EmbeddedInputService();
        var voterController = new VoterController(votingServer);
        voterController.setSounds(com.lumi2021.urna.audio.SoundPlayer.create());

        int port = Integer.parseInt(System.getenv().getOrDefault("MESARIO_PORT", "7070"));
        String token = System.getenv("MESARIO_TOKEN");
        if (token == null || token.isBlank()) token = String.format("%06d", new SecureRandom().nextInt(1_000_000));

        var mesarioWeb = new MesarioWebServer(new EmbeddedPoolWorkerController(votingServer), port, token);
        context.bulletins().setDisplay(doc -> mesarioWeb.publishBulletin(doc.toText()));

        var containerRef = new AtomicReference<VotingScreenEmbeddedContainer>();
        mesarioWeb.setConnectionListener(connected -> SwingUtilities.invokeLater(() -> {
            var container = containerRef.get();
            if (container != null) container.setMesarioConnected(connected);
        }));

        mesarioWeb.start();
        printMesarioAddresses(port, token);
        String mesarioLink = firstMesarioLink(port, token);

        ScheduledExecutorService keypadScanner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "keypad-scan");
            t.setDaemon(true);
            return t;
        });

        keypadScanner.scheduleWithFixedDelay(() -> {
            try {
                inputService.update();
            }
            catch (Exception e) {
                log.error("Falha na varredura do teclado", e);
            }
        }, 0, 10, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            keypadScanner.shutdownNow();
            inputService.close();
            mesarioWeb.close();
        }));

        SwingUtilities.invokeLater(() -> {
            var container = new VotingScreenEmbeddedContainer(inputService, voterController);
            container.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            container.addWindowListener(new ExitOnClose());

            containerRef.set(container);
            container.setMesarioLink(mesarioLink);
            container.setMesarioConnected(mesarioWeb.isConnected());
            container.showFullScreen();

            votingServer.startClockTicker();
        });
    }

    private static String firstMesarioLink(int port, String token) {
        try {
            for (Inet4Address address : NetworkUtils.getLocalIPv4Addresses()) {
                return String.format("http://%s:%d/?token=%s", address.getHostAddress(), port, token);
            }
        }
        catch (Exception e) {
            log.warn("Não foi possível obter o endereço de rede para o QR Code", e);
        }

        log.warn("Sem endereço de rede: o QR Code do mesário não será exibido.");
        return null;
    }

    private static void printMesarioAddresses(int port, String token) {
        System.out.println("Terminal do mesário (abra num navegador da mesma rede):");
        try {
            for (Inet4Address address : NetworkUtils.getLocalIPv4Addresses()) {
                System.out.printf("  http://%s:%d/?token=%s%n", address.getHostAddress(), port, token);
            }
        }
        catch (Exception e) {
            log.warn("Não foi possível listar os endereços de rede", e);
        }
        System.out.printf("  http://localhost:%d/?token=%s%n", port, token);
    }
}
