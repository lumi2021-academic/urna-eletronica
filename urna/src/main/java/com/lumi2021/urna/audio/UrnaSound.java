package com.lumi2021.urna.audio;

/** Sons da urna e o arquivo de cada um em {@code resources/audio/} (hoje são provisórios). */
public enum UrnaSound {
    /** Bipe curto a cada tecla do eleitor. */
    TECLA("tecla.wav"),
    /** Bipe ao confirmar um voto (vereador ou prefeito). */
    CONFIRMA("confirma.wav"),
    /** Som longo do fim da votação (tela FIM). */
    FIM("fim.wav");

    private final String file;

    UrnaSound(String file) { this.file = file; }

    public String resource() { return "/audio/" + file; }
}
