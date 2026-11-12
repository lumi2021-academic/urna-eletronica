package com.lumi2021.urna;

import com.lumi2021.urna.application.BallotBox;
import com.lumi2021.urna.application.UrnaRepositories;
import com.lumi2021.urna.application.VotingServer;
import com.lumi2021.urna.application.VotingService;
import com.lumi2021.urna.controller.VoterController;
import com.lumi2021.urna.controller.poolWorker.DesktopPoolWorkerController;
import com.lumi2021.urna.controller.poolWorker.PoolWorkerController;
import com.lumi2021.urna.infra.firebase.FirebaseConnection;
import com.lumi2021.urna.infra.firebase.RegistroVotacaoService;
import com.lumi2021.urna.platform.DesktopInputService;
import com.lumi2021.urna.platform.EmbeddedInputService;
import com.lumi2021.urna.view.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.*;

public class Program {

    static PoolWorkerView poolWorkerView;
    static VoterView voterView;
    static BulletinView bulletinView;

    static PoolWorkerController pwController;
    static VoterController voterController;
    static VotingScreenEmbeddedContainer votingScreenEmbeddedContainer;

    static BallotBox ballotBox;

    static FirebaseConnection firebase;
    static UrnaRepositories repositories;
    static VotingService votingService;
    static VotingServer votingServer;


    public static void main(String[] args) throws Exception {

        boolean isEmbedded = args.length >= 1 && (args[0].equals("--embedded") || args[0].equals("-e"));

        try {
            firebase = FirebaseConnection.abrirDoAmbiente();
            repositories = UrnaRepositories.carregarNaInicializacao(firebase);

            votingService = new VotingService(new RegistroVotacaoService(
                    firebase.firestore(), firebase.eleicaoId()));

            votingServer = new VotingServer(
                    repositories.candidatos(),
                    repositories.eleitores(),
                    votingService.asGateway()
            );

            Runtime.getRuntime().addShutdownHook(new Thread(firebase::close));
            System.out.printf("Dados carregados do Firebase (eleicao %s): %d candidatos, %d eleitores.%n",
                    repositories.eleicaoId(), repositories.totalCandidatos(), repositories.totalEleitores());
        }
        catch (Exception e) {
            System.err.println("Nao foi possivel carregar os dados do Firebase: " + e.getMessage());
            System.err.println("Verifique as variáveis de ambiente GOOGLE_APPLICATION_CREDENTIALS, " +
                    "FIREBASE_PROJECT_ID e ELEICAO_ID.");
            if (firebase != null) firebase.close();
            System.exit(1);
        }

        votingServer.setPresidentId("000000000000");


        WindowAdapter closeHandler = new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                for (Window window : Window.getWindows()) {
                    window.dispose();
                }
            }
        };

        if (!isEmbedded) {
            // Executando no modo desktop

            var pwInputService = new DesktopInputService();
            var voterInputService = new DesktopInputService();

            ballotBox = new BallotBox();
            var result = ballotBox.emitBulletin();
            //IO.println(result);

            SwingUtilities.invokeLater(() -> {
                voterController = new VoterController(votingServer);

                pwController = new DesktopPoolWorkerController(votingServer);

                poolWorkerView = new PoolWorkerView(pwInputService, pwController);
                voterView = new VoterView(voterInputService, voterController);
                bulletinView = new BulletinView(result);

                poolWorkerView.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                voterView.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                //bulletinView.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

                poolWorkerView.addWindowListener(closeHandler);
                voterView.addWindowListener(closeHandler);
                //bulletinView.addWindowListener(closeHandler);

                Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                int total = poolWorkerView.getWidth() + voterView.getWidth() + 20;
                int left = Math.max(screen.x, screen.x + (screen.width - total) / 2);
                poolWorkerView.setLocation(left, screen.y + Math.max(0, (screen.height - poolWorkerView.getHeight()) / 2));
                voterView.setLocation(left + poolWorkerView.getWidth() + 20,
                        screen.y + Math.max(0, (screen.height - voterView.getHeight()) / 2));

                poolWorkerView.setVisible(true);
                voterView.setVisible(true);
                bulletinView.setVisible(true);

                try {
                    votingServer.iniciarSessaoEleitoral(firebase.eleicaoId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        else {
            // Executando no modo embarcado

            var inputService = new EmbeddedInputService();

            voterController = new VoterController(votingServer);
            votingScreenEmbeddedContainer = new VotingScreenEmbeddedContainer(inputService);

            ballotBox = new BallotBox();

            var executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(() -> {
                try {
                    inputService.update();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 10, TimeUnit.MILLISECONDS);

            SwingUtilities.invokeLater(() -> {
                votingScreenEmbeddedContainer.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                votingScreenEmbeddedContainer.addWindowListener(closeHandler);
                votingScreenEmbeddedContainer.setVisible(true);
            });
        }

    }

}
