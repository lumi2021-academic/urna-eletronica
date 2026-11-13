package com.lumi2021.urna.view;

import java.awt.Font;
import java.io.InputStream;

final class UrnaFonts {

    private static final Font REGULAR = load("/fonts/AtkinsonHyperlegible-Regular.ttf", Font.PLAIN);
    private static final Font BOLD = load("/fonts/AtkinsonHyperlegible-Bold.ttf", Font.BOLD);

    private UrnaFonts() { }

    static Font regular(float size) { return REGULAR.deriveFont(size); }

    static Font bold(float size) { return BOLD.deriveFont(size); }

    private static Font load(String resource, int fallbackStyle) {
        try (InputStream in = UrnaFonts.class.getResourceAsStream(resource)) {
            if (in != null) return Font.createFont(Font.TRUETYPE_FONT, in);
        }
        catch (Exception ignored) {}

        return new Font("SansSerif", fallbackStyle, 12);
    }
}
