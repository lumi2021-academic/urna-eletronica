package com.lumi2021.urna.firebase.backend;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ValidacaoVotacaoTest {
    private static final String ID = UUID.randomUUID().toString();

    @Test void aceitaVotoValidoEBranco() {
        var dados = new VotacaoRequest(ID, List.of(
            new VotoRequest("VEREADOR", "VALIDO", "12001"),
            new VotoRequest("PREFEITO", "BRANCO", null)));
        assertEquals(2, ValidacaoVotacao.validar(dados).size());
    }

    @Test void aceitaVotoNulo() {
        var dados = new VotacaoRequest(ID, List.of(
            new VotoRequest("VEREADOR", "NULO", "99999"),
            new VotoRequest("PREFEITO", "NULO", "99")));
        assertEquals("NULO", ValidacaoVotacao.validar(dados).get("PREFEITO").tipo());
    }

    @Test void rejeitaCargoDuplicado() {
        var dados = new VotacaoRequest(ID, List.of(
            new VotoRequest("VEREADOR", "BRANCO", null),
            new VotoRequest("VEREADOR", "NULO", "99999")));
        assertThrows(ValidacaoVotacao.VotacaoException.class, () -> ValidacaoVotacao.validar(dados));
    }

    @Test void rejeitaVotoValidoComNumeroErrado() {
        var dados = new VotacaoRequest(ID, List.of(
            new VotoRequest("VEREADOR", "VALIDO", "14"),
            new VotoRequest("PREFEITO", "BRANCO", null)));
        assertThrows(ValidacaoVotacao.VotacaoException.class, () -> ValidacaoVotacao.validar(dados));
    }

    @Test void rejeitaSessaoInvalida() {
        var dados = new VotacaoRequest("123", List.of(
            new VotoRequest("VEREADOR", "BRANCO", null),
            new VotoRequest("PREFEITO", "BRANCO", null)));
        assertThrows(ValidacaoVotacao.VotacaoException.class, () -> ValidacaoVotacao.validar(dados));
    }

    @Test void rejeitaBrancoComNumero() {
        var dados = new VotacaoRequest(ID, List.of(
            new VotoRequest("VEREADOR", "BRANCO", "12001"),
            new VotoRequest("PREFEITO", "BRANCO", null)));
        assertThrows(ValidacaoVotacao.VotacaoException.class, () -> ValidacaoVotacao.validar(dados));
    }
}
