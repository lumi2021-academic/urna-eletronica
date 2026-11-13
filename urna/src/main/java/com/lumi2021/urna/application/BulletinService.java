package com.lumi2021.urna.application;

import com.lumi2021.urna.domain.CandidateRepository;
import com.lumi2021.urna.domain.models.bulletinDocument.BulletinDocument;
import com.lumi2021.urna.infra.firebase.ApuracaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Consumer;

public final class BulletinService {

    private static final Logger log = LoggerFactory.getLogger(BulletinService.class);
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private final ApuracaoService apuracao;
    private final CandidateRepository candidates;
    private final BallotBox ballotBox;
    private final String eleicaoId;

    private volatile Consumer<BulletinDocument> display = doc -> { };

    public BulletinService(ApuracaoService apuracao, CandidateRepository candidates,
                           BallotBox ballotBox, String eleicaoId) {
        this.apuracao = apuracao;
        this.candidates = candidates;
        this.ballotBox = ballotBox;
        this.eleicaoId = eleicaoId;
    }

    public void setDisplay(Consumer<BulletinDocument> display) {
        this.display = display == null ? doc -> { } : display;
    }

    public void emit(boolean partial) {
        try {
            BulletinDocument document = ballotBox.emitBulletin(
                    apuracao.carregar(), candidates, LocalDateTime.now(BRASILIA), partial);

            String text = document.toText();
            System.out.println(text);

            Path file = Path.of((partial ? "boletim-parcial-" : "boletim-") + eleicaoId + ".txt");
            Files.writeString(file, text);
            System.out.println("Boletim salvo em " + file.toAbsolutePath());

            display.accept(document);
        }
        catch (Exception e) {
            log.error("Falha ao emitir o boletim de urna", e);
        }
    }
}
