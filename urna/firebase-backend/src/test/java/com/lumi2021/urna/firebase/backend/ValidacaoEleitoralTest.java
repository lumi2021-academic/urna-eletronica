package com.lumi2021.urna.firebase.backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidacaoEleitoralTest {
    @Test void vereadorExigeCincoDigitos() {
        assertTrue(ValidacaoEleitoral.numeroValido("VEREADOR", "12001"));
        assertFalse(ValidacaoEleitoral.numeroValido("VEREADOR", "12"));
        assertFalse(ValidacaoEleitoral.numeroValido("VEREADOR", "12abc"));
    }
    @Test void prefeitoExigeDoisDigitos() {
        assertTrue(ValidacaoEleitoral.numeroValido("PREFEITO", "13"));
        assertFalse(ValidacaoEleitoral.numeroValido("PREFEITO", "12001"));
        assertFalse(ValidacaoEleitoral.numeroValido("PREFEITO", null));
    }
    @Test void cargosPermitidos() {
        assertTrue(ValidacaoEleitoral.cargoValido("PREFEITO"));
        assertTrue(ValidacaoEleitoral.cargoValido("VEREADOR"));
        assertFalse(ValidacaoEleitoral.cargoValido("PRESIDENTE"));
    }
}
