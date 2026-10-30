package com.lumi2021.urna.view;

import com.lumi2021.urna.platform.InputService;

import javax.swing.*;
import java.awt.*;

public class VoterView extends JFrame {

    // Componentes do Visor que a lógica externa precisará atualizar
    private JLabel lblCargo;
    private JPanel panelDigitos;
    private JLabel[] lblDigitos;
    private JLabel lblNome;
    private JLabel lblPartido;
    private JLabel lblMensagem;
    private JLabel lblFoto;
    private JPanel panelFoto;
    private JPanel panelInstrucoes;

    // Botões do Teclado para adicionar listeners externos
    private JButton[] btnsNumericos = new JButton[10];
    private JButton btnBranco;
    private JButton btnCorrige;
    private JButton btnConfirma;

    public VoterView(InputService inputService) {
        super("Terminal da urna");
        //this.inputService = inputService;
        //inputService.install(this);

        configurarJanela();
        inicializarComponentes();
    }

    private void configurarJanela() {
        setTitle("Urna Eletrônica - Justiça Eleitoral");
        setSize(920, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(new BorderLayout(15, 15));
        getContentPane().setBackground(new Color(215, 215, 210));
    }

    private void inicializarComponentes() {
        // ==================== VISOR (ESQUERDA) ====================
        JPanel panelVisor = new JPanel(new BorderLayout(10, 10));
        panelVisor.setBackground(new Color(245, 245, 235));
        panelVisor.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100), 4),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JPanel panelConteudo = new JPanel(null);
        panelConteudo.setOpaque(false);

        JLabel lblTitulo = new JLabel("SEU VOTO VAI PARA");
        lblTitulo.setFont(new Font("Arial", Font.PLAIN, 14));
        lblTitulo.setBounds(10, 10, 200, 20);
        panelConteudo.add(lblTitulo);

        lblCargo = new JLabel("VEREADOR");
        lblCargo.setFont(new Font("Arial", Font.BOLD, 22));
        lblCargo.setBounds(10, 35, 250, 30);
        panelConteudo.add(lblCargo);

        JLabel lblNumTxt = new JLabel("Número:");
        lblNumTxt.setFont(new Font("Arial", Font.PLAIN, 16));
        lblNumTxt.setBounds(10, 85, 70, 30);
        panelConteudo.add(lblNumTxt);

        panelDigitos = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panelDigitos.setOpaque(false);
        panelDigitos.setBounds(85, 80, 260, 45);
        panelConteudo.add(panelDigitos);

        lblNome = new JLabel("Nome: --");
        lblNome.setFont(new Font("Arial", Font.PLAIN, 16));
        lblNome.setBounds(10, 140, 320, 25);
        panelConteudo.add(lblNome);

        lblPartido = new JLabel("Partido: --");
        lblPartido.setFont(new Font("Arial", Font.PLAIN, 16));
        lblPartido.setBounds(10, 170, 320, 25);
        panelConteudo.add(lblPartido);

        lblMensagem = new JLabel("");
        lblMensagem.setFont(new Font("Arial", Font.BOLD, 22));
        lblMensagem.setForeground(Color.RED);
        lblMensagem.setBounds(10, 210, 320, 30);
        panelConteudo.add(lblMensagem);

        // Painel da Foto
        panelFoto = new JPanel(new BorderLayout());
        panelFoto.setBounds(340, 10, 130, 160);
        panelFoto.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
        panelFoto.setBackground(Color.WHITE);

        lblFoto = new JLabel("", SwingConstants.CENTER);
        lblFoto.setFont(new Font("Serif", Font.PLAIN, 70));
        panelFoto.add(lblFoto, BorderLayout.CENTER);
        panelConteudo.add(panelFoto);

        // Rodapé de Instruções
        panelInstrucoes = new JPanel(new GridLayout(3, 1));
        panelInstrucoes.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, Color.BLACK));
        panelInstrucoes.setOpaque(false);

        panelInstrucoes.add(new JLabel("Aperte a tecla:"));
        panelInstrucoes.add(new JLabel("  CONFIRMA para CONFIRMAR este voto"));
        panelInstrucoes.add(new JLabel("  CORRIGE para REINICIAR este voto"));

        panelVisor.add(panelConteudo, BorderLayout.CENTER);
        panelVisor.add(panelInstrucoes, BorderLayout.SOUTH);
        add(panelVisor, BorderLayout.CENTER);

        // ==================== TECLADO (DIREITA) ====================
        JPanel panelTeclado = new JPanel(new BorderLayout(0, 15));
        panelTeclado.setBackground(new Color(30, 30, 30));
        panelTeclado.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panelTeclado.setPreferredSize(new Dimension(320, 0));

        JLabel lblTopo = new JLabel("JUSTIÇA ELEITORAL", SwingConstants.CENTER);
        lblTopo.setForeground(Color.WHITE);
        lblTopo.setFont(new Font("Arial", Font.BOLD, 18));
        panelTeclado.add(lblTopo, BorderLayout.NORTH);

        // Botões Numéricos
        JPanel panelNum = new JPanel(new GridLayout(4, 3, 10, 10));
        panelNum.setOpaque(false);

        String[] layoutTeclado = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", ""};
        for (String txt : layoutTeclado) {
            if (txt.isEmpty()) {
                panelNum.add(new JLabel(""));
            } else {
                int num = Integer.parseInt(txt);
                btnsNumericos[num] = new JButton(txt);
                btnsNumericos[num].setFont(new Font("Arial", Font.BOLD, 20));
                btnsNumericos[num].setBackground(new Color(20, 20, 20));
                btnsNumericos[num].setForeground(Color.WHITE);
                btnsNumericos[num].setFocusPainted(false);
                panelNum.add(btnsNumericos[num]);
            }
        }

        // Botões de Ação
        JPanel panelAcoes = new JPanel(new GridLayout(1, 3, 8, 0));
        panelAcoes.setOpaque(false);
        panelAcoes.setPreferredSize(new Dimension(0, 55)); // Altura ajustada para evitar cortes

        btnBranco = new JButton("BRANCO");
        btnBranco.setFont(new Font("Arial", Font.BOLD, 10));
        btnBranco.setBackground(Color.WHITE);
        btnBranco.setForeground(Color.BLACK);
        btnBranco.setFocusPainted(false);

        btnCorrige = new JButton("CORRIGE");
        btnCorrige.setFont(new Font("Arial", Font.BOLD, 10));
        btnCorrige.setBackground(new Color(230, 80, 40));
        btnCorrige.setForeground(Color.BLACK);
        btnCorrige.setFocusPainted(false);

        btnConfirma = new JButton("CONFIRMA");
        btnConfirma.setFont(new Font("Arial", Font.BOLD, 9));
        btnConfirma.setBackground(new Color(0, 180, 80));
        btnConfirma.setForeground(Color.BLACK);
        btnConfirma.setFocusPainted(false);

        panelAcoes.add(btnBranco);
        panelAcoes.add(btnCorrige);
        panelAcoes.add(btnConfirma);

        JPanel panelCentroTeclado = new JPanel(new BorderLayout(0, 15));
        panelCentroTeclado.setOpaque(false);
        panelCentroTeclado.add(panelNum, BorderLayout.CENTER);
        panelCentroTeclado.add(panelAcoes, BorderLayout.SOUTH);

        panelTeclado.add(panelCentroTeclado, BorderLayout.CENTER);
        add(panelTeclado, BorderLayout.EAST);

        // Inicializa com 5 caixas por padrão (Vereador)
        prepararCaixasDigitos(5);
    }

    // ==================== MÉTODOS PÚBLICOS DE INTERFACE ====================
    // (Outro membro do grupo chamará estes métodos para atualizar a tela)

    public void setCargo(String cargo) {
        lblCargo.setText(cargo.toUpperCase());
    }

    public void prepararCaixasDigitos(int quantidade) {
        panelDigitos.removeAll();
        lblDigitos = new JLabel[quantidade];

        for (int i = 0; i < quantidade; i++) {
            JLabel box = new JLabel("", SwingConstants.CENTER);
            box.setPreferredSize(new Dimension(35, 40));
            box.setFont(new Font("Arial", Font.BOLD, 22));
            box.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            box.setOpaque(true);
            box.setBackground(Color.WHITE);
            lblDigitos[i] = box;
            panelDigitos.add(box);
        }
        panelDigitos.revalidate();
        panelDigitos.repaint();
    }

    public void atualizarDigito(int indice, String valor) {
        if (indice >= 0 && indice < lblDigitos.length) {
            lblDigitos[indice].setText(valor);
        }
    }

    public void limparDigitos() {
        for (JLabel lbl : lblDigitos) {
            lbl.setText("");
        }
    }

    public void setInfoCandidato(String nome, String partido, String fotoOuEmoji) {
        lblNome.setText("Nome: " + nome);
        lblPartido.setText("Partido: " + partido);
        if (fotoOuEmoji != null && !fotoOuEmoji.isEmpty()) {
            lblFoto.setText(fotoOuEmoji);
            panelFoto.setVisible(true);
        } else {
            panelFoto.setVisible(false);
        }
    }

    public void setMensagemAviso(String mensagem) {
        lblMensagem.setText(mensagem);
    }

    public void exibirTelaFim() {
        panelDigitos.setVisible(false);
        lblCargo.setText("");
        lblNome.setText("");
        lblPartido.setText("");
        lblMensagem.setText("F I M");
        lblMensagem.setFont(new Font("Arial", Font.BOLD, 60));
        lblMensagem.setBounds(110, 80, 300, 80);
        panelFoto.setVisible(false);
        panelInstrucoes.setVisible(false);
    }

    // Getters para que os outros desenvolvedores adicionem os ActionListeners aos botões
    public JButton getBtnNumero(int numero) { return btnsNumericos[numero]; }
    public JButton getBtnBranco() { return btnBranco; }
    public JButton getBtnCorrige() { return btnCorrige; }
    public JButton getBtnConfirma() { return btnConfirma; }
}