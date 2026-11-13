package com.lumi2021.urna.application;

import com.lumi2021.urna.infra.firebase.ApuracaoService;
import com.lumi2021.urna.infra.firebase.FirebaseConnection;
import com.lumi2021.urna.infra.firebase.RegistroVotacaoService;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Monta e guarda as peças da urna que são iguais nos dois modos: Firebase, dados em memória,
 * {@link VotingServer} e {@link BulletinService}. Os modos ({@code mode/}) só adicionam interface.
 *
 * <p>Uso: {@link #conectar()} → {@link #iniciarEleicao()} → entregar ao modo.
 */
public final class UrnaContext {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final String PRESIDENTE_PADRAO = "000000000000";

    private final FirebaseConnection firebase;
    private final UrnaRepositories repositories;
    private final VotingServer votingServer;
    private final BallotBox ballotBox = new BallotBox();
    private final BulletinService bulletins;

    private UrnaContext(FirebaseConnection firebase, UrnaRepositories repositories) {
        this.firebase = firebase;
        this.repositories = repositories;

        var votingService = new VotingService(new RegistroVotacaoService(firebase.firestore(), firebase.eleicaoId()));
        this.votingServer = new VotingServer(
                repositories.candidatos(),
                repositories.eleitores(),
                votingService.asGateway());
        this.bulletins = new BulletinService(
                new ApuracaoService(firebase.firestore(), firebase.eleicaoId()),
                repositories.candidatos(),
                ballotBox,
                firebase.eleicaoId());
    }

    /** Conecta ao Firebase, baixa e valida o snapshot de candidatos, partidos e eleitores. */
    public static UrnaContext conectar() throws Exception {
        FirebaseConnection firebase = FirebaseConnection.abrirDoAmbiente();
        try {
            UrnaRepositories repositories = UrnaRepositories.carregarNaInicializacao(firebase);
            Runtime.getRuntime().addShutdownHook(new Thread(firebase::close));

            System.out.printf("Dados carregados do Firebase (eleicao %s): %d candidatos, %d eleitores.%n",
                    repositories.eleicaoId(), repositories.totalCandidatos(), repositories.totalEleitores());
            return new UrnaContext(firebase, repositories);
        }
        catch (Exception e) {
            firebase.close();
            throw e;
        }
    }

    /**
     * Configura a mesa (presidente, total de eleitores), liga os boletins ao menu do mesário e
     * abre a eleição no Firestore. Deve ser chamado antes de exibir qualquer tela.
     */
    public void iniciarEleicao() throws Exception {
        votingServer.setPresidentId(System.getenv().getOrDefault("PRESIDENTE_ID", PRESIDENTE_PADRAO));
        votingServer.setTotalVoters(repositories.totalEleitores());

        ballotBox.initialize(null, 0, LocalDateTime.now(BRASILIA), null, 0, 0, repositories.totalEleitores());
        votingServer.setClosedListener(() -> bulletins.emit(false));
        votingServer.setPartialBulletinListener(() -> bulletins.emit(true));

        votingServer.iniciarSessaoEleitoral(firebase.eleicaoId());
    }

    public VotingServer votingServer() { return votingServer; }
    public BulletinService bulletins() { return bulletins; }

    public void close() {
        firebase.close();
    }
}
