package com.lumi2021.urna.firebase.backend;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ValidacaoVotacao {
    private ValidacaoVotacao() { }

    public static Map<String, VotoRequest> validar(VotacaoRequest request) {
        if (request == null || request.sessaoId() == null) {
            throw new VotacaoException(400, "sessaoId obrigatorio");
        }
        try {
            UUID id = UUID.fromString(request.sessaoId());
            if (!id.toString().equals(request.sessaoId())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new VotacaoException(400, "sessaoId deve ser UUID canonico");
        }
        List<VotoRequest> votos = request.votos();
        if (votos == null || votos.size() != 2) {
            throw new VotacaoException(400, "Informe exatamente dois votos: VEREADOR e PREFEITO");
        }
        Map<String, VotoRequest> porCargo = new LinkedHashMap<>();
        for (VotoRequest voto : votos) {
            if (voto == null || !ValidacaoEleitoral.cargoValido(voto.cargo())) {
                throw new VotacaoException(400, "Cargo invalido");
            }
            if (!"VALIDO".equals(voto.tipo()) && !"BRANCO".equals(voto.tipo())
                    && !"NULO".equals(voto.tipo())) {
                throw new VotacaoException(400, "Tipo deve ser VALIDO, BRANCO ou NULO");
            }
            if ("VALIDO".equals(voto.tipo()) &&
                    !ValidacaoEleitoral.numeroValido(voto.cargo(), voto.numero())) {
                throw new VotacaoException(400, "Voto VALIDO requer numero eleitoral valido");
            }
            if ("BRANCO".equals(voto.tipo()) &&
                    voto.numero() != null && !voto.numero().isBlank()) {
                throw new VotacaoException(400, "Voto BRANCO nao aceita numero");
            }
            if ("NULO".equals(voto.tipo()) && voto.numero() != null &&
                    !voto.numero().isBlank() &&
                    !ValidacaoEleitoral.numeroValido(voto.cargo(), voto.numero())) {
                throw new VotacaoException(400, "Numero de voto NULO com formato invalido");
            }
            if (porCargo.putIfAbsent(voto.cargo(), voto) != null) {
                throw new VotacaoException(400, "Cargo informado mais de uma vez");
            }
        }
        if (!porCargo.containsKey("VEREADOR") || !porCargo.containsKey("PREFEITO")) {
            throw new VotacaoException(400, "Faltam VEREADOR e/ou PREFEITO");
        }
        return Map.copyOf(porCargo);
    }

    public static final class VotacaoException extends RuntimeException {
        private final int httpStatus;
        public VotacaoException(int httpStatus, String mensagem) {
            super(mensagem);
            this.httpStatus = httpStatus;
        }
        public int httpStatus() { return httpStatus; }
    }
}
