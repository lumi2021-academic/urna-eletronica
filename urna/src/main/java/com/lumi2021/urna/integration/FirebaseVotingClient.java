package com.lumi2021.urna.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class FirebaseVotingClient {

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper json;

    public FirebaseVotingClient(String baseUrl) {

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Endereco da API obrigatorio"
            );
        }

        this.baseUrl = baseUrl.replaceAll("/+$", "");

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.json = new ObjectMapper();
    }

    public String verificarApi()
            throws IOException, InterruptedException {

        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/health"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> resposta = http.send(
                requisicao,
                HttpResponse.BodyHandlers.ofString()
        );

        if (resposta.statusCode() != 200) {
            throw new IOException(
                    "Falha na API. HTTP: "
                    + resposta.statusCode()
                    + " - " + resposta.body()
            );
        }

        return resposta.body();
    }

    public ConfirmacaoVotacaoData registrarVotacao(
            VotacaoData votacao,
            String token
    ) throws IOException, InterruptedException {

        if (votacao == null) {
            throw new IllegalArgumentException(
                    "Dados da votacao obrigatorios"
            );
        }

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Token de votacao obrigatorio"
            );
        }

        String corpoJson = json.writeValueAsString(votacao);

        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/votacoes"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Voting-Token", token)
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        corpoJson,
                        StandardCharsets.UTF_8
                    )
                )
                .build();

        HttpResponse<String> resposta = http.send(
                requisicao,
                HttpResponse.BodyHandlers.ofString()
        );

        if (resposta.statusCode() != 201) {
            throw new IOException(
                    "Falha ao registrar votacao. HTTP "
                    + resposta.statusCode()
                    + ": " + resposta.body()
            );
        }

        return json.readValue(
                resposta.body(),
                ConfirmacaoVotacaoData.class
        );
    }
}
