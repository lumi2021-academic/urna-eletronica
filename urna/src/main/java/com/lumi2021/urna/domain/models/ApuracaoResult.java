package com.lumi2021.urna.domain.models;

import java.util.Map;

/** Resultado da apuração por cargo (lido do Firestore ao encerrar a votação). */
public record ApuracaoResult(Map<String, CargoResult> porCargo) {

    /**
     * @param votosPorNumero votos válidos de cada candidato, indexados pelo número
     */
    public record CargoResult(long validos, long brancos, long nulos, long total,
                              Map<String, Long> votosPorNumero) { }
}
