package br.edu.utfpr.config;

import com.google.gson.Gson;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileLoader {

    private static final Gson GSON = new Gson();

    public static <T> T loadJsonFromConfig(String filename, Class<T> clazz) {
        try (Reader reader = new FileReader("config/" + filename)) {
            return GSON.fromJson(reader, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String loadDocumentFromConfig(String filename) {
        try {
            return Files.readString(Path.of("config/" + filename));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
