package com.lumi2021.urna.firebase.backend;

public record CandidateInfo(
        String numero,
        String cargo,
        String pokemonId,
        String nome,
        String imagemUrl,
        String partidoId,
        String partido,
        String sigla
) {}
