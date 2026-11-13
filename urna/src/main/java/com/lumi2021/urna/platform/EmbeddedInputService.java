package com.lumi2021.urna.platform;

import com.lumi2021.urna.platform.events.InputAction;
import com.lumi2021.urna.platform.events.InputListener;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.CopyOnWriteArrayList;

public class EmbeddedInputService implements InputService, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedInputService.class);

    private JFrame window;

    private final CopyOnWriteArrayList<InputListener> listeners = new CopyOnWriteArrayList<>();

    private InputMap inputMap;
    private ActionMap actionMap;

    private Context pi4j;
    private MatrixKeypad keypad;


    private final boolean[][] lastRaw = new boolean[4][4];
    private final boolean[][] reported = new boolean[4][4];

    public EmbeddedInputService() {
        try {
            pi4j = Pi4J.newAutoContext();
            keypad = new MatrixKeypad(pi4j);
            log.info("Teclado matricial inicializado.");
        }
        catch (Exception | LinkageError e) {
            log.warn("GPIO indisponível ({}). Usando apenas o teclado do computador.", e.toString());
            closeGpio();
        }
    }

    public boolean hasKeypad() {
        return keypad != null;
    }

    public void install(JFrame window) {
        if (window == null) return;
        if (this.window == window) return;

        uninstall();

        this.window = window;

        JRootPane root = window.getRootPane();

        inputMap = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        actionMap = root.getActionMap();
        
        for (int digit = 0; digit <= 9; digit++) {
            InputAction action = InputAction.valueOf("KEY_" + digit);
            bind(KeyEvent.VK_0 + digit, action);
            bind(KeyEvent.VK_NUMPAD0 + digit, action);
        }

        bind(KeyEvent.VK_ENTER, InputAction.CONFIRMA);
        bind(KeyEvent.VK_BACK_SPACE, InputAction.CORRIGE);
        bind(KeyEvent.VK_ESCAPE, InputAction.BRANCO);

        if (window instanceof InputListener listener) addListener(listener);
    }

    private void bind(int keyCode, InputAction action) {
        KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, 0);

        inputMap.put(keyStroke, action);

        actionMap.put(action, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispatch(action);
            }
        });
    }

    public void uninstall() {
        if (window == null) return;

        if (window instanceof InputListener listener)
            removeListener(listener);

        inputMap.clear();

        for (InputAction action : InputAction.values())
            actionMap.remove(action);

        window = null;
        inputMap = null;
        actionMap = null;
    }

    public void addListener(InputListener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(InputListener listener) {
        listeners.remove(listener);
    }

    private void dispatch(InputAction action) {
        for (InputListener listener : listeners) listener.onInput(action);
    }

    public void emit(InputAction action) {
        dispatch(action);
    }

    public void update() {
        if (keypad == null) return;

        InputAction[][] now = keypad.scan();

        for (int r = 0; r < now.length; r++) {
            for (int c = 0; c < now[r].length; c++) {
                boolean down = now[r][c] != InputAction.NONE;

                if (down == lastRaw[r][c] && down != reported[r][c]) {
                    reported[r][c] = down;
                    if (down) dispatch(now[r][c]);
                }
                lastRaw[r][c] = down;
            }
        }
    }

    @Override
    public void close() {
        closeGpio();
    }

    private void closeGpio() {
        try {
            if (keypad != null) keypad.close();
        }
        catch (Exception ignored) { }

        try {
            if (pi4j != null) pi4j.shutdown();
        }
        catch (Exception ignored) { }

        keypad = null;
        pi4j = null;
    }
}
