package br.edu.utfpr;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpClientFacade {

    private final HttpClient httpClient;

    public HttpClientFacade() {
        this.httpClient = HttpClient.newHttpClient();
    }


    public byte[] post(String endpoint, byte[] body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            return doRequest(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send POST request to " + endpoint, e);
        }
    }

    public byte[] get(String endpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .GET()
                    .build();

            return doRequest(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send GET request to " + endpoint, e);
        }
    }

    private byte[] doRequest(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("Failed to send GET request to " + request.uri(), e);
        }
    }
}
