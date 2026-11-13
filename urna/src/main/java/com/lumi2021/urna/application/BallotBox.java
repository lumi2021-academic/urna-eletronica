package com.lumi2021.urna.application;

import com.lumi2021.urna.domain.CandidateRepository;
import com.lumi2021.urna.domain.models.ApuracaoResult;
import com.lumi2021.urna.domain.models.ApuracaoResult.CargoResult;
import com.lumi2021.urna.domain.models.VoteEntry;
import com.lumi2021.urna.domain.models.bulletinDocument.BulletinDocument;
import com.lumi2021.urna.domain.models.bulletinDocument.BulletinElement;
import com.lumi2021.urna.domain.models.bulletinDocument.BulletinLine;
import com.lumi2021.urna.domain.models.bulletinDocument.BulletinTextMode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class BallotBox {

    // Atributos
    private List<VoteEntry> voteEntries;
    private String municipality; // Municipio em que a Urna será utilizada.
    private int municipalityCode; // Código do municipio no qual a urna será usada.
    private LocalDateTime openingDateTime; // Data e hora do inicio da votação.
    private LocalDateTime closingDataTime; // Data e hora do encerramento da votação.
    private int electoralZone; // Zona eleitoral em que a urna será usada.
    private int section; // Seção eleitoral onde a urna será usada.
    private int eligibleVoters; // Quantidade total de votos elegiveis.

    // Inicializa todos os atributos quando a urna for ativada
    public void initialize(
        String municipality,
        int municipalityCode,
        LocalDateTime openingDateTime,
        LocalDateTime closingDateTime,
        int electoralZon,
        int section,
        int eligibleVoters
    ) {
        this.municipality = municipality;
        this.municipalityCode = municipalityCode;
        this.openingDateTime = openingDateTime;
        this.closingDataTime = closingDateTime;
        this.electoralZone = electoralZon;
        this.section = section;
        this.eligibleVoters = eligibleVoters;
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


    public BulletinDocument emitBulletin(ApuracaoResult result, CandidateRepository candidates, LocalDateTime closing) {
        return emitBulletin(result, candidates, closing, false);
    }

    public BulletinDocument emitBulletin(
            ApuracaoResult result,
            CandidateRepository candidates,
            LocalDateTime closing, boolean partial
    ) {
        var builder = new BulletinBuilder();
        DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm:ss");

        builder.center("1a. VIA", BulletinTextMode.Bold);
        builder.left("");

        builder.center("Justiça Eleitoral", BulletinTextMode.Bold);
        if (municipality != null && !municipality.isBlank()) builder.center(municipality);
        builder.left("");

        builder.center(partial ? "Boletim de Urna - PARCIAL" : "Boletim de Urna", partial ? BulletinTextMode.Bold : BulletinTextMode.Normal);
        if (partial) builder.center("(votação em andamento)");
        builder.left("");

        builder.center("Eleições municipais " + closing.getYear());
        builder.center("(" + closing.format(date) + ")");
        builder.left("");

        if (eligibleVoters > 0)
            builder.row(new int[] { 28, 12 }, "Eleitores aptos", builder.padLeft(String.valueOf(eligibleVoters), 12));

        if (openingDateTime != null)
            builder.row(new int[] { 20, 20 }, "Abertura", builder.padLeft(openingDateTime.format(time), 20));

        builder.row(new int[] { 20, 20 }, partial ? "Emitido em" : "Encerramento", builder.padLeft(closing.format(time), 20));
        builder.left("");

        builder.fillLine('=');

        for (Map.Entry<String, CargoResult> entry : result.porCargo().entrySet()) {
            CargoResult cargo = entry.getValue();
            builder.left("");
            builder.centerFill(entry.getKey(), '-', BulletinTextMode.Bold);

            builder.row(new int[] { 23, 1, 8, 1, 7 },
                    "Nome do candidato", "",
                    builder.padLeft("Num cand", 8), "",
                    builder.padLeft("Votos", 7));

            List<Map.Entry<String, Long>> ranking = new ArrayList<>(cargo.votosPorNumero().entrySet());
            ranking.sort(Map.Entry.<String, Long>comparingByValue().reversed()
                    .thenComparing(Map.Entry.comparingByKey()));

            for (var candidate : ranking) {
                String name = candidates.buscarPorNumero(entry.getKey(), candidate.getKey())
                        .map(c -> c.nome().toUpperCase())
                        .orElse("(INATIVO)");
                builder.row(new int[] { 23, 1, 8, 1, 7 },
                        name, "",
                        builder.padLeft(candidate.getKey(), 8), "",
                        builder.padLeft(String.valueOf(candidate.getValue()), 7));
            }

            builder.fillLine('-');
            builder.row(new int[] { 28, 12 }, "Votos válidos", builder.padLeft(String.valueOf(cargo.validos()), 12));
            builder.row(new int[] { 28, 12 }, "Votos em branco", builder.padLeft(String.valueOf(cargo.brancos()), 12));
            builder.row(new int[] { 28, 12 }, "Votos nulos", builder.padLeft(String.valueOf(cargo.nulos()), 12));
            builder.row(BulletinTextMode.Bold, new int[] { 28, 12 }, "Total", builder.padLeft(String.valueOf(cargo.total()), 12));
        }

        builder.left("");
        builder.fillLine('=');

        return builder.toDocument();
    }

    private final class BulletinBuilder {
        private static final int BULLETIN_WIDTH = 40;

        private final List<BulletinElement> elements = new ArrayList<>();

        public void left(String text) { left(text, BulletinTextMode.Normal); }
        public void left(String text, BulletinTextMode textMode) {
            elements.add(new BulletinLine(padRight(text, BULLETIN_WIDTH), textMode));
        }

        public void right(String text) { right(text, BulletinTextMode.Normal); }
        public void right(String text, BulletinTextMode textMode) {
            elements.add(new BulletinLine(padLeft(text, BULLETIN_WIDTH), textMode));
        }

        public void center(String text) { centerFill(text, ' ', BulletinTextMode.Normal); }
        public void center(String text, BulletinTextMode textMode) { centerFill(text, ' ', textMode); }
        public void centerFill(String text, char fill) { centerFill(text, fill, BulletinTextMode.Normal); }
        public void centerFill(String text, char fill, BulletinTextMode textMode) {
            String value = truncate(text, BULLETIN_WIDTH);

            int totalPadding = BULLETIN_WIDTH - value.length();
            int leftPadding = totalPadding / 2;
            int rightPadding = totalPadding - leftPadding;

            var v = new StringBuilder()
                    .repeat(fill, leftPadding)
                    .append(value)
                    .repeat(fill, rightPadding)
                    .toString();

            elements.add(new BulletinLine(v, textMode));
        }

        public void fillLine(char c) {
            elements.add(new BulletinLine(Character.toString(c).repeat(BULLETIN_WIDTH), BulletinTextMode.Normal));
        }

        public void row(int[] widths, String... values) { row(BulletinTextMode.Normal, widths, values); }
        public void row(BulletinTextMode textMode, int[] widths, String... values) {
            if (widths.length != values.length) {
                throw new IllegalArgumentException("A quantidade de larguras deve corresponder à quantidade de valores.");
            }

            int totalWidth = 0;
            for (int width : widths) {
                if (width < 0) throw new IllegalArgumentException("A largura de uma coluna não pode ser negativa.");
                totalWidth += width;
            }

            if (totalWidth > BULLETIN_WIDTH) {
                throw new IllegalArgumentException("A largura total das colunas excede o boletim.");
            }

            var buffer = new StringBuilder();

            for (int i = 0; i < values.length; i++) {
                buffer.append(padRight(values[i], widths[i]));
            }

            elements.add(new BulletinLine(buffer.toString(), textMode));
        }

        public String padLeft(String text, int width) {
            String value = truncate(text, width);
            return " ".repeat(width - value.length()) + value;
        }

        public String padRight(String text, int width) {
            String value = truncate(text, width);
            return value + " ".repeat(width - value.length());
        }

        public String truncate(String text, int width) {
            String value = text == null ? "null" : text;

            if (width <= 0) return "";
            if (value.length() <= width) return value;
            return value.substring(0, width);
        }

        public BulletinDocument toDocument() {
            return new BulletinDocument(List.copyOf(elements));
        }
    }
}
