package com.lumi2021.urna.integration;

import java.util.List;

public record VotacaoData(
        String sessaoId,
        List<VotoData> votos
) {
}
