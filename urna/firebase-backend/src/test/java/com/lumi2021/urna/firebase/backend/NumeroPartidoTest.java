package com.lumi2021.urna.firebase.backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumeroPartidoTest {
    @Test void vereadorDeveComecarComNumeroPartido() {
        assertTrue(ValidacaoEleitoral.numeroDoPartido("VEREADOR", "12001", "12"));
        assertTrue(ValidacaoEleitoral.numeroDoPartido("VEREADOR", "13006", "13"));
        assertFalse(ValidacaoEleitoral.numeroDoPartido("VEREADOR", "12001", "13"));
    }
    @Test void prefeitoDeveUsarNumeroExatoDoPartido() {
        assertTrue(ValidacaoEleitoral.numeroDoPartido("PREFEITO", "14", "14"));
        assertFalse(ValidacaoEleitoral.numeroDoPartido("PREFEITO", "14", "13"));
        assertFalse(ValidacaoEleitoral.numeroDoPartido("PREFEITO", "14001", "14"));
    }
    @Test void valoresInvalidosSaoRejeitados() {
        assertFalse(ValidacaoEleitoral.numeroDoPartido("PREFEITO", null, "14"));
        assertFalse(ValidacaoEleitoral.numeroDoPartido("PREFEITO", "14", null));
        assertFalse(ValidacaoEleitoral.numeroDoPartido("GOVERNADOR", "14", "14"));
    }
}
