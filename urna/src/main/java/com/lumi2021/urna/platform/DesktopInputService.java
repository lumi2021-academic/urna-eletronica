package com.lumi2021.urna.platform;

import com.lumi2021.urna.platform.events.InputAction;
import com.lumi2021.urna.platform.events.InputListener;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.CopyOnWriteArrayList;

public class DesktopInputService implements InputService {

    private JFrame window;

    private final CopyOnWriteArrayList<InputListener> listeners = new CopyOnWriteArrayList<>();

    private InputMap inputMap;
    private ActionMap actionMap;

    public void install(JFrame window) {
        if (window == null) return;

        if (this.window == window) return;

        uninstall();

        this.window = window;

        JRootPane root = window.getRootPane();

        inputMap = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        actionMap = root.getActionMap();

        // Teclas numéricas
        for (int digit = 0; digit <= 9; digit++) {
            InputAction action = InputAction.valueOf("KEY_" + digit);

            bind(KeyEvent.VK_0 + digit, action);

            bind(KeyEvent.VK_NUMPAD0 + digit, action);
        }

        // Teclas de controle
        bind(KeyEvent.VK_ENTER, InputAction.CONFIRMA);
        bind(KeyEvent.VK_BACK_SPACE, InputAction.CORRIGE);
        bind(KeyEvent.VK_ESCAPE, InputAction.BRANCO);

        if (window instanceof InputListener listener)
            addListener(listener);
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

}
