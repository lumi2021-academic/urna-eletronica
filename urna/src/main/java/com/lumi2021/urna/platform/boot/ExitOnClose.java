package com.lumi2021.urna.platform.boot;

import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

final class ExitOnClose extends WindowAdapter {
    @Override
    public void windowClosing(WindowEvent e) {
        for (Window window : Window.getWindows()) window.dispose();
        System.exit(0);
    }
}
