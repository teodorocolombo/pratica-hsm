package br.edu.utfpr.config;

public record KeyConfig(String firstKeyPairId,
                        String secondKeyPairId,
                        String sharedSecretKeyId,
                        String certificatePath,
                        String certificatePassword) {
}
