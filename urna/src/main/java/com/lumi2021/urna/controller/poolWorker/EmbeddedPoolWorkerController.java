package com.lumi2021.urna.controller.poolWorker;

import com.lumi2021.urna.application.VotingServer;
import com.lumi2021.urna.platform.events.InputAction;
import com.lumi2021.urna.view.models.MesarioScreenModel;

import java.util.function.Consumer;

public class EmbeddedPoolWorkerController implements PoolWorkerController {

    private final VotingServer server;

    public EmbeddedPoolWorkerController(VotingServer server) {
        this.server = server;
    }

    public void handleInput(InputAction input) {
        server.handleMesarioInput(input);
    }
    public void setViewStateListener(Consumer<MesarioScreenModel> listener) {
        server.setMesarioStateListener(listener);
    }
}
