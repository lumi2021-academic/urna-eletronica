package com.lumi2021.urna.firebase.backend;

public final class ValidacaoEleitoral {
    private ValidacaoEleitoral() { }

    public static boolean cargoValido(String cargo) {
        return "VEREADOR".equals(cargo) || "PREFEITO".equals(cargo);
    }

    public static boolean numeroValido(String cargo, String numero) {
        if (numero == null) return false;
        if ("VEREADOR".equals(cargo)) return numero.matches("[0-9]{5}");
        if ("PREFEITO".equals(cargo)) return numero.matches("[0-9]{2}");
        return false;
    }

    /** Vereador: os dois primeiros digitos identificam o partido; prefeito: numero do partido. */
    public static boolean numeroDoPartido(String cargo, String numero, String numeroPartido) {
        if (!numeroValido(cargo, numero) || numeroPartido == null ||
                !numeroPartido.matches("[0-9]{2}")) return false;
        if ("PREFEITO".equals(cargo)) return numero.equals(numeroPartido);
        return numero.startsWith(numeroPartido);
    }

}
