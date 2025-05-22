package br.edu.utfpr.config;

public record ServerConfig(String host,
                           int port) {

    public String getUrl() {
        return host + ":" + port;
    }
}
