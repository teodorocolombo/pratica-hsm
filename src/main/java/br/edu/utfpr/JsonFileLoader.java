package br.edu.utfpr;

import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.FileReader;

public class JsonFileLoader {

    private static final Gson GSON = new Gson();

    public static <T> T loadFromConfig(String filename, Class<T> clazz) {
        try {
            FileReader reader = new FileReader("config/" + filename);
            return GSON.fromJson(reader, clazz);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
