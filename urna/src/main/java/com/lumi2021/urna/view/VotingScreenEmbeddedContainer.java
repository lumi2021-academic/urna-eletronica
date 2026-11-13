package com.lumi2021.urna.view;

import com.lumi2021.urna.controller.VoterController;
import com.lumi2021.urna.platform.InputService;
import com.lumi2021.urna.platform.events.InputAction;
import com.lumi2021.urna.platform.events.InputListener;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class VotingScreenEmbeddedContainer extends JFrame implements InputListener {

    private final VoterController controller;
    private final VotingScreen screen = new VotingScreen();

    public VotingScreenEmbeddedContainer(InputService inputService, VoterController controller) {
        super("Tela da urna");
        this.controller = controller;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setUndecorated(true);
        setContentPane(screen);

        setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                new Point(0, 0), "blank")
        );

        controller.setDisplay(screen::setModel);
        inputService.install(this);

        toFront();
        requestFocus();
        requestFocusInWindow();
    }

    private boolean[][] qr = null;
    private boolean mesarioConnected = false;

    public void setMesarioLink(String url) {
        try {
            qr = url == null ? null : QrCode.encode(url);
        }
        catch (IllegalArgumentException e) {
            qr = null;
        }
        refreshQr();
    }

    public void setMesarioConnected(boolean connected) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setMesarioConnected(connected));
            return;
        }

        mesarioConnected = connected;
        refreshQr();
    }

    private void refreshQr() {
        screen.setQrCode(mesarioConnected ? null : qr);
    }

    public void showFullScreen() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (device.isFullScreenSupported()) {
            device.setFullScreenWindow(this);
        }
        else {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            setVisible(true);
        }
        screen.requestFocusInWindow();
    }

    @Override
    public void onInput(InputAction action) {
        switch (action) {
            case CONFIRMA -> controller.confirm();
            case CORRIGE -> controller.correct();
            case BRANCO -> controller.blank();
            case NONE -> { }
            default -> controller.digit(InputAction.toInteger(action));
        }
    }
}
