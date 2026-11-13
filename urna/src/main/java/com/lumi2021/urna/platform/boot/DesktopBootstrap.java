package com.lumi2021.urna.platform.boot;

import com.lumi2021.urna.application.UrnaContext;
import com.lumi2021.urna.controller.VoterController;
import com.lumi2021.urna.controller.poolWorker.DesktopPoolWorkerController;
import com.lumi2021.urna.platform.DesktopInputService;
import com.lumi2021.urna.view.BulletinView;
import com.lumi2021.urna.view.PoolWorkerView;
import com.lumi2021.urna.view.VoterView;

import javax.swing.*;
import java.awt.*;

public final class DesktopBootstrap implements UrnaBootstrap {

    @Override
    public void start(UrnaContext context) {
        var votingServer = context.votingServer();
        var windowCloser = new ExitOnClose();

        context.bulletins().setDisplay(doc ->
                SwingUtilities.invokeLater(() -> new BulletinView(doc).setVisible(true)));

        SwingUtilities.invokeLater(() -> {
            var voterController = new VoterController(votingServer);
            voterController.setSounds(com.lumi2021.urna.audio.SoundPlayer.create());
            var pwController = new DesktopPoolWorkerController(votingServer);

            var poolWorkerView = new PoolWorkerView(new DesktopInputService(), pwController);
            var voterView = new VoterView(new DesktopInputService(), voterController);

            poolWorkerView.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            voterView.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            poolWorkerView.addWindowListener(windowCloser);
            voterView.addWindowListener(windowCloser);

            // Calcula a melhor posição pra as janelas não ficarem sobrepostas
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int total = poolWorkerView.getWidth() + voterView.getWidth() + 20;
            int left = Math.max(screen.x, screen.x + (screen.width - total) / 2);
            poolWorkerView.setLocation(left, screen.y + Math.max(0, (screen.height - poolWorkerView.getHeight()) / 2));
            voterView.setLocation(left + poolWorkerView.getWidth() + 20,
                    screen.y + Math.max(0, (screen.height - voterView.getHeight()) / 2));

            poolWorkerView.setVisible(true);
            voterView.setVisible(true);

            votingServer.startClockTicker();
        });
    }
}
