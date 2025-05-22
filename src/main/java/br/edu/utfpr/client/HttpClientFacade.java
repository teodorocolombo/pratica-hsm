package br.edu.utfpr.client;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class HttpClientFacade {

    private final HttpClient httpClient;

    public HttpClientFacade(String certFilePath) throws Exception {
        try (InputStream certInputStream = new FileInputStream(certFilePath)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate caCert = (X509Certificate) cf.generateCertificate(certInputStream);

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("serverCert", caCert);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            this.httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();
        }
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
