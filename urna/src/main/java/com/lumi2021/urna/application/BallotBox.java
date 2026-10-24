package com.lumi2021.urna.application;

//EM CONSTRUÇÃO

import java.time.LocalDateTime;

public class BallotBox {


    private String municipality; //Municipio em que a Urna será utilizada.
    private int municipalityCode; //Código do municipio no qual a urna será usada.
    private LocalDateTime openingDateTime; //Local, data e hora do inicio da votação.
    private LocalDateTime closingDataTime = LocalDateTime.now(); //Local, data e hora do encerramento da votação.
    private int electoralZone; //Zona eleitoral em que a urna será usada.
    private int section; //Seção eleitoral onde a urna será usada.
    private int eligibleVoters; //Quantidade total de votos elegiveis.


    public void initialize(
            String municipality,
            int municipalityCode,
            LocalDateTime openingDateTime,
            LocalDateTime closingDataTime,
            int electoralZon,
            int section,
            int eligibleVoters
            ) {

    }

    public String getMunicipality(){
        return municipality;
    }

    public int getMunicipalityCode(){
        return municipalityCode;
    }

    public LocalDateTime getOpeningDateTime() {
        return openingDateTime;
    }

    public int getElectoralZone(){
        return electoralZone;
    }

    public int getSection(){
        return section;
    }

    public int getEligibleVoters(){
        return eligibleVoters;
    }

    public String emitBulletin(){
        var sb = new StringBuilder();
        var width = 40;

        sb.append(TextAlign.center("Justiça Eleitoral", width)+ "\n");
        sb.append(TextAlign.center("Tribunal Regional Eleitoral [ZZ]", width)+"\n");
        sb.append("=".repeat(width)+"\n");



        return sb.toString();

    }




    private final class TextAlign {
        private TextAlign() {}

        public static String left(String text, int width) {
            text = text == null ? "null" : text;
            if (text.length() >= width) return text;
            return text + " ".repeat(width - text.length());
        }

        public static String row(int[] widths, String... values) {
            if (widths.length != values.length) {
                throw new IllegalArgumentException(
                        "A quantidade de larguras deve corresponder à quantidade de valores."
                );
            }

            StringBuilder result = new StringBuilder();

            for (int i = 0; i < values.length; i++) {
                result.append(left(values[i], widths[i]));
            }

            return result.toString();
        }

        public static String right(String text, int width) {
            text = text == null ? "null" : text;
            if (text.length() >= width) return text;
            return " ".repeat(width - text.length()) + text;
        }

        public static String center(String text, int width) {
            text = text == null ? "null" : text;
            if (text.length() >= width) return text;

            int totalPadding = width - text.length();
            int leftPadding = totalPadding / 2;
            int rightPadding = totalPadding - leftPadding;

            return " ".repeat(leftPadding)
                    + text
                    + " ".repeat(rightPadding);
        }

        public static String pad(String text, int width) {
            return left(text, width);
        }

        public static String truncate(String text, int width) {
            text = text == null ? "null" : text;
            if (width <= 0) return "";
            if (text.length() <= width) return text;
            return text.substring(0, width);
        }
    }
}