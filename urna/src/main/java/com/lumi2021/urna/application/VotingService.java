package com.lumi2021.urna.application;

import com.lumi2021.urna.infra.firebase.RegistroVotacaoService;
import com.lumi2021.urna.infra.firebase.RegistroVotacaoService.ConfirmacaoVotacao;
import com.lumi2021.urna.infra.firebase.RegistroVotacaoService.SessaoCriada;
import com.lumi2021.urna.infra.firebase.VotacaoRequest;
import com.lumi2021.urna.infra.firebase.VotoRequest;

import java.util.List;

public class VotingService {
    private final RegistroVotacaoService registro;

    public VotingService(RegistroVotacaoService registro) {
        this.registro = registro;
    }

    public SessaoCriada abrirSessao() throws Exception {
        return registro.abrirSessao();
    }

    public void abrirEleicao(String eleicaoId) throws Exception {
        registro.abrirEleicao(eleicaoId);
    }

    public ConfirmacaoVotacao registrar(String sessaoId, List<VotoRequest> votos) throws Exception {
        return registro.registrar(new VotacaoRequest(sessaoId, votos));
    }

    public VotingGateway asGateway() {
        return new VotingGateway() {
            @Override
            public String abrirSessao() throws Exception {
                return VotingService.this.abrirSessao().sessaoId();
            }

            @Override
            public void abrirEleicao(String eleicaoId) throws Exception {
                VotingService.this.abrirEleicao(eleicaoId);
            }

            @Override
            public void registrar(String sessaoId, List<VotoRequest> votos) throws Exception {
                VotingService.this.registrar(sessaoId, votos);
            }
        };
    }
}
