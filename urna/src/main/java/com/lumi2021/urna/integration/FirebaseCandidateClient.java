package com.lumi2021.urna.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public final class FirebaseCandidateClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;

    public FirebaseCandidateClient() {
        this(System.getenv().getOrDefault("URNA_API_URL", "http://127.0.0.1:7070"));
    }
    public FirebaseCandidateClient(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Endereco da API obrigatorio");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public Optional<CandidateData> buscarCandidato(String cargo, String numero)
            throws IOException, InterruptedException {
        String query = "cargo=" + codificar(cargo) + "&numero=" + codificar(numero);
        URI uri = URI.create(baseUrl + "/api/candidatos?" + query);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) return Optional.empty();
        if (res.statusCode() != 200) {
            throw new IOException("Erro ao consultar candidato: HTTP " + res.statusCode());
        }
        return Optional.of(json.readValue(res.body(), CandidateData.class));
    }

    public List<CandidateData> listarCandidatos(String cargo)
            throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + "/api/candidatos/cargo/" + codificar(cargo));
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Erro ao listar candidatos: HTTP " + res.statusCode());
        }
        return json.readValue(res.body(), new TypeReference<List<CandidateData>>() {});
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor == null ? "" : valor, StandardCharsets.UTF_8);
    }
}
