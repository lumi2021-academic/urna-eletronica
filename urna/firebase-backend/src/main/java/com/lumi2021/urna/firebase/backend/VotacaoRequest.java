package com.lumi2021.urna.firebase.backend;

import java.util.List;

public record VotacaoRequest(String sessaoId, List<VotoRequest> votos) { }
