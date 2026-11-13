package com.lumi2021.urna.view;

import com.lumi2021.urna.controller.VoterController;
import com.lumi2021.urna.platform.InputService;
import com.lumi2021.urna.platform.events.InputAction;
import com.lumi2021.urna.platform.events.InputListener;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class VoterView extends JFrame implements InputListener {
    private final InputService inputService;
    private VoterController voterController;

    private VotingScreen screen;

    private JButton[] btnsNumericos = new JButton[10];
    private JButton btnBranco;
    private JButton btnCorrige;
    private JButton btnConfirma;

    public VoterView(InputService inputService, VoterController voterController) {
        super("Terminal da urna");
        this.inputService = inputService;
        inputService.install(this);

        this.voterController = voterController;

        configurarJanela();
        inicializarComponentes();

        voterController.setDisplay(screen::setModel);
    }

    @Override
    public void onInput(InputAction action) {
        switch (action) {
            case CONFIRMA -> voterController.confirm();
            case CORRIGE -> voterController.correct();
            case BRANCO -> voterController.blank();
            case NONE -> { }
            default -> voterController.digit(InputAction.toInteger(action));
        }
    }

    private void configurarJanela() {
        setTitle("Urna Eletrônica");
        setSize(650, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(new Color(215, 215, 210));

        ImageIcon icon = new ImageIcon(Objects.requireNonNull(
                getClass().getResource("/icons/urna-icon.png")));
        setIconImage(icon.getImage());
    }

    private void inicializarComponentes() {
        // ==================== VISOR ====================

        screen = new VotingScreen();
        screen.setBorder(BorderFactory.createLineBorder(Color.BLACK, 8));

        JPanel screenContainer = new JPanel(null) {
            @Override
            public void doLayout() {
                double aspectRatio = 800.0 / 500.0;

                int availableWidth = getWidth();
                int availableHeight = getHeight();

                int width = Math.min(availableWidth, (int) (availableHeight * aspectRatio));

                int height = (int) (width / aspectRatio);

                int x = (availableWidth - width) / 2;
                int y = (availableHeight - height) / 2;

                screen.setBounds(x, y, width, height);
            }
        };

        screenContainer.setBackground(new Color(0xD7D7D2));
        screenContainer.add(screen);

        // ==================== TECLADO ====================

        JPanel panelTeclado = new JPanel(new BorderLayout(0, 15));
        panelTeclado.setBackground(new Color(0xE8E9E5));
        panelTeclado.setBorder(BorderFactory.createEmptyBorder(15, 30, 20, 30));

        JLabel lblTopo = new JLabel("JUSTIÇA ELEITORAL POKEMON", SwingConstants.CENTER);

        lblTopo.setForeground(Color.BLACK);
        lblTopo.setFont(new Font("Arial", Font.BOLD, 16));

        panelTeclado.add(lblTopo, BorderLayout.NORTH);

        // ==================== ÁREA DOS BOTÕES ====================

        JPanel panelCentroTeclado = new JPanel(new BorderLayout(15, 0));
        panelCentroTeclado.setOpaque(false);

        panelCentroTeclado.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));

        // Teclado numérico
        JPanel panelNum = new JPanel(new GridLayout(4, 3, 8, 8));
        panelNum.setOpaque(false);

        String[] layoutTeclado = {
                "1", "2", "3",
                "4", "5", "6",
                "7", "8", "9",
                "", "0", ""
        };

        for (String txt : layoutTeclado) {
            if (txt.isEmpty()) {
                panelNum.add(new JLabel(""));
                continue;
            }

            int num = Integer.parseInt(txt);
            JButton btn = criarBotaoNumerico(txt);
            btn.addActionListener(e -> voterController.digit(num));

            btnsNumericos[num] = btn;
            panelNum.add(btn);
        }

        panelCentroTeclado.add(panelNum, BorderLayout.CENTER);

        // Botões de ação
        JPanel panelAcoes = new JPanel(new GridLayout(3, 1, 0, 8));
        panelAcoes.setOpaque(false);
        panelAcoes.setPreferredSize(new Dimension(130, 0));

        btnBranco = criarBotaoAcao("BRANCO", Color.WHITE, Color.BLACK, 11);
        btnCorrige = criarBotaoAcao("CORRIGE", new Color(230, 80, 40), Color.BLACK, 11);
        btnConfirma = criarBotaoAcao("CONFIRMA", new Color(0, 180, 80), Color.BLACK, 10);

        btnBranco.addActionListener(e -> voterController.blank());
        btnCorrige.addActionListener(e -> voterController.correct());
        btnConfirma.addActionListener(e -> voterController.confirm());

        panelAcoes.add(btnBranco);
        panelAcoes.add(btnCorrige);
        panelAcoes.add(btnConfirma);

        panelCentroTeclado.add(panelAcoes, BorderLayout.EAST);

        panelTeclado.add(panelCentroTeclado, BorderLayout.CENTER);

        // ==================== LAYOUT PRINCIPAL ====================

        JPanel mainPane = new JPanel(new GridLayout(2, 1, 0, 0));

        mainPane.add(screenContainer);
        mainPane.add(panelTeclado);

        setContentPane(mainPane);
        instalarAtalhosDeTeclado(mainPane);
    }

    private void instalarAtalhosDeTeclado(JComponent root) {
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        for (int d = 0; d <= 9; d++) {
            final int digit = d;
            am.put("digit" + d, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    voterController.digit(digit);
                }
            });
            im.put(KeyStroke.getKeyStroke(String.valueOf(d)), "digit" + d);
            im.put(KeyStroke.getKeyStroke("NUMPAD" + d), "digit" + d);
        }

        am.put("branco", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                voterController.blank();
            }
        });
        am.put("corrige", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                voterController.correct();
            }
        });
        am.put("confirma", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                voterController.confirm();
            }
        });
        im.put(KeyStroke.getKeyStroke("B"), "branco");
        im.put(KeyStroke.getKeyStroke("BACK_SPACE"), "corrige");
        im.put(KeyStroke.getKeyStroke("ENTER"), "confirma");
    }

    // ==================== FACTORY METHODS ====================

    private JButton criarBotaoNumerico(String texto) {
        JButton btn = new JButton(texto);

        btn.setFont(new Font("Arial", Font.BOLD, 18));
        btn.setBackground(new Color(20, 20, 20));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFocusable(false);

        return btn;
    }

    private JButton criarBotaoAcao(
            String texto,
            Color background,
            Color foreground,
            int fontSize
    ) {
        JButton btn = new JButton(texto);

        btn.setFont(new Font("Arial", Font.BOLD, fontSize));
        btn.setBackground(background);
        btn.setForeground(foreground);
        btn.setFocusPainted(false);
        btn.setFocusable(false);

        return btn;
    }

}
