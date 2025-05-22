package br.edu.utfpr.server;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;


import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class HttpsServerFacade {

    private final HttpsServer server;

    public HttpsServerFacade(int port, String keystorePath, String keystorePassword) {
        try {
            this.server = HttpsServer.create(new InetSocketAddress(port), 0);

            KeyStore ks = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, keystorePassword.toCharArray());
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, keystorePassword.toCharArray());

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters params) {
                    SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
                    params.setSSLParameters(sslParameters);
                }
            });

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize HTTPS server", e);
        }
    }

    public void addGetHandler(String path, Supplier<byte[]> handler) {
        server.createContext(path, exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                byte[] responseBody = handler.get();

                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, responseBody.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody);
                }
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            }
        });
    }

    public void addPostHandler(String path, Function<byte[], byte[]> handler) {
        server.createContext(path, exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                byte[] responseBody = handler.apply(requestBody);

                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, responseBody.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody);
                }
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            }
        });
    }

    public void addPostHandler(String path, BiFunction<byte[], Map<String, String>, byte[]> handler) {
        server.createContext(path, exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                URI requestURI = exchange.getRequestURI();
                Map<String, String> queryParams = parseQueryParams(requestURI.getRawQuery());

                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                byte[] responseBody = handler.apply(requestBody, queryParams);

                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, responseBody.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody);
                }
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            }
        });
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;

        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            String key = java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.UTF_8);
            String value = pair.length > 1
                    ? java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8)
                    : "";
            params.put(key, value);
        }
        return params;
    }


    public void start() {
        try {
            server.start();
            System.out.println("HTTP Server started on port " + server.getAddress().getPort());
        } catch (Exception e) {
            throw new RuntimeException("Failed to start HTTP server", e);
        }
    }

    public void stop(int delaySeconds) {
        try {
            server.stop(delaySeconds);
            System.out.println("HTTP Server stopped.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to stop HTTP server", e);
        }
    }
}
