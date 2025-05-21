package br.edu.utfpr;

public record ServerConfig(String host,
                           int port) {

    public String getUrl() {
        return host + ":" + port;
    }
}
