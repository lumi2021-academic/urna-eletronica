package com.lumi2021.urna.view.models;

public sealed interface VotingScreenModel {

    Mode mode();

    enum Mode {
        WAITING, VOTING, SUBMITTING, FINISHED
    }

    record Waiting(String message) implements VotingScreenModel {
        @Override
        public Mode mode() {
            return Mode.WAITING;
        }
    }

    record Submitting(String message) implements VotingScreenModel {
        public Submitting() {
            this("Registrando o seu voto...");
        }

        @Override
        public Mode mode() {
            return Mode.SUBMITTING;
        }
    }

    record Finished() implements VotingScreenModel {
        @Override
        public Mode mode() {
            return Mode.FINISHED;
        }
    }

    /**
     * @param step          índice (a partir de 0) do cargo em votação, para a barra de progresso
     * @param viceName      nome do vice (só cargos majoritários; null se não houver dado)
     * @param viceImageUrl  foto do vice (null mostra só o espaço reservado)
     * @param confirmLocked true durante a pausa de segurança ("Confira seu voto"), em que CONFIRMA é ignorada
     */
    record Voting(
            String cargoLabel,
            int digitCount,
            String digits,
            String candidateName,
            String partyName,
            String imageUrl,
            String headline,
            String detail,
            int step,
            boolean confirmLocked,
            String viceName,
            String viceImageUrl
    ) implements VotingScreenModel {
        public Voting {
            digits = digits == null ? "" : digits;
        }

        @Override
        public Mode mode() {
            return Mode.VOTING;
        }
    }
}