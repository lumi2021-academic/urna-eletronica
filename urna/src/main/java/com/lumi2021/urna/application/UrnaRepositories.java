package com.lumi2021.urna.application;

import com.lumi2021.urna.domain.CandidateRepository;
import com.lumi2021.urna.domain.CandidatesRepository;
import com.lumi2021.urna.domain.ElectorRepository;
import com.lumi2021.urna.domain.InMemoryElectorRepository;
import com.lumi2021.urna.domain.models.CandidateEntity;
import com.lumi2021.urna.domain.models.ElectoralPartyEntity;
import com.lumi2021.urna.domain.models.ElectorEntity;
import com.lumi2021.urna.infra.firebase.FirebaseConnection;
import com.lumi2021.urna.infra.firebase.FirestoreUrnaSnapshotService;
import com.lumi2021.urna.integration.UrnaSnapshotData;
import java.util.List;

public final class UrnaRepositories {
    private final String eleicaoId;
    private final CandidatesRepository candidatos;
    private final InMemoryElectorRepository eleitores;

    private UrnaRepositories(String eleicaoId, CandidatesRepository candidatos,
                             InMemoryElectorRepository eleitores) {
        this.eleicaoId = eleicaoId;
        this.candidatos = candidatos;
        this.eleitores = eleitores;
    }

    public static UrnaRepositories carregarNaInicializacao(FirebaseConnection firebase) throws Exception {
        return aPartirDoSnapshot(new FirestoreUrnaSnapshotService(
                firebase.firestore(), firebase.eleicaoId()).carregar());
    }

    public static UrnaRepositories aPartirDoSnapshot(UrnaSnapshotData dados) {
        if (dados == null || dados.eleicaoId() == null || dados.eleicaoId().isBlank() ||
                dados.candidatos() == null || dados.partidos() == null || dados.eleitores() == null) {
            throw new IllegalArgumentException("Dados de inicializacao incompletos");
        }
        List<CandidateEntity> candidatos = dados.candidatos().stream().map(c -> new CandidateEntity(
                c.numero(), c.cargo(), c.pokemonId(), c.nome(), c.imagemUrl(),
                c.partidoId(), c.partido(), c.sigla(), c.ativo())).toList();
        List<ElectoralPartyEntity> partidos = dados.partidos().stream().map(p ->
                new ElectoralPartyEntity(p.id(), p.numero(), p.nome(), p.sigla(), p.ativo())).toList();
        List<ElectorEntity> eleitores = dados.eleitores().stream().map(e ->
                new ElectorEntity(e.id(), e.ativo())).toList();
        if (candidatos.isEmpty() || partidos.isEmpty() || eleitores.isEmpty()) {
            throw new IllegalStateException("Cadastro de candidatos, partidos ou eleitores vazio");
        }
        return new UrnaRepositories(dados.eleicaoId(),
                new CandidatesRepository(candidatos, partidos),
                new InMemoryElectorRepository(eleitores));
    }

    public CandidateRepository candidatos() { return candidatos; }
    public ElectorRepository eleitores() { return eleitores; }
    public String eleicaoId() { return eleicaoId; }
    public int totalCandidatos() { return candidatos.totalCarregados(); }
    public int totalEleitores() { return eleitores.totalCarregados(); }
}