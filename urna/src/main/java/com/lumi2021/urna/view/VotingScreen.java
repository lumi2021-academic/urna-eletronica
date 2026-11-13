package com.lumi2021.urna.view;

import com.lumi2021.urna.view.models.VotingScreenModel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VotingScreen extends JPanel {

    private static final int W = 800, H = 500;

    private static final Color BG = Color.WHITE;
    private static final Color INK = Color.BLACK;
    private static final Color MUTED = new Color(90, 90, 90);
    private static final Color FRAME = new Color(140, 140, 140);
    private static final Color PLACEHOLDER = new Color(238, 238, 238);
    private static final Color BAR_DONE = new Color(60, 60, 60);
    private static final Color BAR_PENDING = new Color(228, 228, 228);


    private static final String[] CARGOS = {"Vereador", "Prefeito"};

    private static final ExecutorService IMAGE_LOADER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "candidate-image-loader");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, Image> images = new ConcurrentHashMap<>();
    private final Set<String> requestedImages = ConcurrentHashMap.newKeySet();

    private VotingScreenModel model = new VotingScreenModel.Waiting(null);

    private boolean[][] qrCode = null;

    public VotingScreen() {
        setBackground(BG);
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
    }

    public void setQrCode(boolean[][] modules) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setQrCode(modules));
            return;
        }

        this.qrCode = modules;
        repaint();
    }

    public void setModel(VotingScreenModel model) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setModel(model));
            return;
        }

        this.model = model;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            double scale = Math.min(getWidth() / (double) W, getHeight() / (double) H);
            g2.translate((getWidth() - W * scale) / 2, (getHeight() - H * scale) / 2);
            g2.scale(scale, scale);
            g2.setClip(0, 0, W, H);

            g2.setColor(BG);
            g2.fillRect(0, 0, W, H);

            switch (model) {
                case VotingScreenModel.Waiting w -> paintWaiting(g2, w);
                case VotingScreenModel.Voting v -> paintVoting(g2, v);
                case VotingScreenModel.Submitting s -> {
                    g2.setColor(INK);
                    g2.setFont(UrnaFonts.bold(30));
                    drawCentered(g2, s.message(), 262);
                    paintBrand(g2);
                }
                case VotingScreenModel.Finished f -> paintFinished(g2);
            }
        }
        finally {
            g2.dispose();
        }
    }

    // ==================== TELAS ====================

    private void paintWaiting(Graphics2D g2, VotingScreenModel.Waiting w) {
        g2.setColor(INK);
        g2.setFont(UrnaFonts.bold(60));
        drawCentered(g2, "AGUARDE", 262);

        g2.setColor(MUTED);
        g2.setFont(UrnaFonts.regular(20));
        drawCentered(g2, "Aguardando a liberação pelo mesário.", 312);

        if (w.message() != null) {
            g2.setColor(INK);
            g2.setFont(UrnaFonts.bold(20));
            drawCentered(g2, w.message(), 366);
        }
        else if (qrCode != null) {
            paintMesarioQr(g2);
        }
        paintBrand(g2);
    }

    private void paintFinished(Graphics2D g2) {
        paintProgress(g2, CARGOS.length);

        g2.setColor(INK);
        g2.setFont(UrnaFonts.bold(190));
        drawCentered(g2, "FIM", 320);

        g2.setFont(UrnaFonts.regular(34));
        drawRight(g2, "VOTOU", W - 36, 430);
        paintBrand(g2);
    }

    private void paintVoting(Graphics2D g2, VotingScreenModel.Voting v) {
        paintProgress(g2, v.step());

        boolean blank = "VOTO EM BRANCO".equals(v.headline());
        boolean wrongNumber = "VOTO NULO".equals(v.headline());
        boolean candidate = v.candidateName() != null;
        boolean majoritarian = "Prefeito".equalsIgnoreCase(v.cargoLabel());

        // SEU VOTO PARA / Cargo
        g2.setColor(INK);
        g2.setFont(UrnaFonts.regular(17));
        g2.drawString("SEU VOTO PARA", 36, 72);
        g2.setFont(UrnaFonts.bold(38));
        g2.drawString(v.cargoLabel(), 36, 114);

        if (blank) {
            g2.setFont(UrnaFonts.bold(62));
            g2.drawString("VOTO EM BRANCO", 36, 290);
        }
        else {
            paintDigitBoxes(g2, v);

            if (candidate) {
                int y = 266;
                y = paintField(g2, "Nome:", v.candidateName(), y);
                if (v.partyName() != null) y = paintField(g2, "Partido:", v.partyName(), y);
                if (majoritarian && v.viceName() != null) paintField(g2, "Vice-Prefeito:", v.viceName(), y);

                paintPhoto(g2, 622, 50, 142, 190, v.imageUrl(), v.cargoLabel());
                if (majoritarian) {
                    paintPhoto(g2, 652, 272, 90, 120, v.viceImageUrl(), "Vice-Prefeito");
                }
            }
            else if (wrongNumber) {
                g2.setColor(INK);
                g2.setFont(UrnaFonts.regular(24));
                g2.drawString("NÚMERO ERRADO", 36, 282);
                g2.setFont(UrnaFonts.bold(62));
                g2.drawString("VOTO NULO", 36, 352);
            }
            else if (v.digits().isEmpty()) {
                g2.setColor(MUTED);
                g2.setFont(UrnaFonts.regular(20));
                g2.drawString("Digite o número do candidato.", 36, 282);
            }
            else if (v.partyName() != null) {
                paintField(g2, "Partido:", v.partyName(), 266);
            }
        }

        if (v.detail() != null) {
            g2.setColor(INK);
            g2.setFont(UrnaFonts.bold(19));
            g2.drawString(v.detail(), 36, 398);
        }

        paintFooter(g2, v, blank);
    }

    // ==================== PEÇAS ====================

    private void paintProgress(Graphics2D g2, int current) {
        int n = CARGOS.length;
        int h = 30;
        int w = W / n;

        for (int i = 0; i < n; i++) {
            boolean done = i < current;
            boolean active = i == current;
            int x = i * w;

            g2.setColor(done ? BAR_DONE : active ? BG : BAR_PENDING);
            g2.fillRect(x, 0, w, h);

            g2.setColor(done ? Color.WHITE : active ? INK : MUTED);
            g2.setFont(active ? UrnaFonts.bold(16) : UrnaFonts.regular(16));
            FontMetrics fm = g2.getFontMetrics();
            String label = (i + 1) + "  " + CARGOS[i];
            g2.drawString(label, x + 14, (h + fm.getAscent() - fm.getDescent()) / 2);
        }

        g2.setColor(INK);
        g2.fillRect(0, h, W, 2);
        if (current < n) g2.fillRect(current * w, h - 4, w, 6);
    }

    private void paintDigitBoxes(Graphics2D g2, VotingScreenModel.Voting v) {
        g2.setColor(INK);
        g2.setFont(UrnaFonts.regular(20));
        g2.drawString("Número:", 36, 156);

        int size = 50, gap = 6, y = 168;
        for (int i = 0; i < v.digitCount(); i++) {
            int bx = 36 + i * (size + gap);

            g2.setColor(Color.WHITE);
            g2.fillRect(bx, y, size, size);
            g2.setColor(INK);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(bx, y, size, size);
            g2.setStroke(new BasicStroke(1f));

            if (i < v.digits().length()) {
                String digit = v.digits().substring(i, i + 1);
                g2.setFont(UrnaFonts.regular(38));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(digit, bx + (size - fm.stringWidth(digit)) / 2, y + 38);
            }
        }
    }

    private int paintField(Graphics2D g2, String label, String value, int y) {
        g2.setColor(INK);
        g2.setFont(UrnaFonts.regular(22));
        String text = label + " " + value;

        float size = 22;
        while (size > 14 && g2.getFontMetrics().stringWidth(text) > 570) {
            size -= 1;
            g2.setFont(UrnaFonts.regular(size));
        }
        g2.drawString(text, 36, y);
        return y + 34;
    }

    private void paintPhoto(Graphics2D g2, int x, int y, int w, int h, String imageUrl, String caption) {
        Image img = imageFor(imageUrl);

        if (img != null && img.getWidth(null) > 0 && img.getHeight(null) > 0) {
            int iw = img.getWidth(null), ih = img.getHeight(null);
            double s = Math.min(w / (double) iw, h / (double) ih);
            int dw = (int) (iw * s), dh = (int) (ih * s);
            g2.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
        }
        else {
            g2.setColor(PLACEHOLDER);
            g2.fillRect(x, y, w, h);
            g2.setColor(FRAME);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(x, y, w, h);
            g2.setColor(MUTED);
            g2.setFont(UrnaFonts.regular(13));
            drawCenteredIn(g2, "FOTO", x, w, y + h / 2 + 5);
        }

        g2.setColor(INK);
        g2.setFont(UrnaFonts.regular(15));
        drawCenteredIn(g2, caption, x, w, y + h + 20);
    }

    private void paintFooter(Graphics2D g2, VotingScreenModel.Voting v, boolean blank) {
        g2.setColor(INK);
        g2.fillRect(0, 424, W, 2);

        if (v.confirmLocked()) {
            g2.setFont(UrnaFonts.bold(30));
            g2.drawString("Confira seu voto", 36, 470);
            paintBrand(g2);
            return;
        }

        g2.setFont(UrnaFonts.bold(15));
        g2.drawString("Aperte a tecla:", 36, 446);

        boolean canConfirm = blank || v.candidateName() != null || "VOTO NULO".equals(v.headline());
        int y = 466;
        if (canConfirm) {
            paintInstruction(g2, 36, y, "VERDE", "para CONFIRMAR este voto");
            y += 20;
        }
        paintInstruction(g2, 36, y, "LARANJA", "para REINICIAR este voto");
        y += 20;

        if (v.digits().isEmpty() && !blank) {
            paintInstruction(g2, 36, y, "BRANCO", "para VOTAR EM BRANCO");
        }
        paintBrand(g2);
    }

    private void paintInstruction(Graphics2D g2, int x, int y, String key, String rest) {
        g2.setColor(INK);
        g2.setFont(UrnaFonts.bold(15));
        g2.drawString(key, x, y);
        int kw = g2.getFontMetrics().stringWidth(key);
        g2.setFont(UrnaFonts.regular(15));
        g2.drawString(" " + rest, x + kw, y);
    }

    private void paintBrand(Graphics2D g2) {
        g2.setColor(INK);
        g2.setFont(UrnaFonts.bold(13));
        drawRight(g2, "JUSTIÇA ELEITORAL POKEMON", W - 36, 485);
    }

    private void paintMesarioQr(Graphics2D g2) {
        int quiet = 4;
        int modules = qrCode.length + quiet * 2;
        int cell = Math.max(1, 130 / modules);
        int box = cell * modules;
        int x0 = W - 36 - box, y0 = 462 - box;

        g2.setColor(Color.WHITE);
        g2.fillRect(x0, y0, box, box);
        g2.setColor(Color.BLACK);

        for (int r = 0; r < qrCode.length; r++) {
            for (int c = 0; c < qrCode.length; c++) {
                if (qrCode[r][c]) g2.fillRect(x0 + (c + quiet) * cell, y0 + (r + quiet) * cell, cell, cell);
            }
        }
        g2.setColor(FRAME);
        g2.drawRect(x0, y0, box, box);

        g2.setColor(INK);
        g2.setFont(UrnaFonts.bold(16));
        drawRight(g2, "Mesário desconectado", x0 - 14, y0 + box / 2 - 4);
        g2.setColor(MUTED);
        g2.setFont(UrnaFonts.regular(15));
        drawRight(g2, "Leia o QR Code para conectar.", x0 - 14, y0 + box / 2 + 17);
    }

    // ==================== UTILITÁRIOS ====================

    private void drawCentered(Graphics2D g2, String text, int baseline) {
        drawCenteredIn(g2, text, 0, W, baseline);
    }

    private void drawCenteredIn(Graphics2D g2, String text, int x, int width, int baseline) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, x + (width - fm.stringWidth(text)) / 2, baseline);
    }

    private void drawRight(Graphics2D g2, String text, int rightX, int baseline) {
        g2.drawString(text, rightX - g2.getFontMetrics().stringWidth(text), baseline);
    }

    private Image imageFor(String url) {
        if (url == null || url.isBlank()) return null;

        Image cached = images.get(url);
        if (cached != null) return cached;

        if (requestedImages.add(url)) {
            IMAGE_LOADER.execute(() -> {
                try {
                    URLConnection connection = URI.create(url).toURL().openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(8000);

                    try (InputStream in = connection.getInputStream()) {
                        BufferedImage loaded = ImageIO.read(in);
                        if (loaded != null) images.put(url, loaded);
                    }
                }
                catch (Exception ignored) {}

                SwingUtilities.invokeLater(this::repaint);
            });
        }
        return null;
    }
}
