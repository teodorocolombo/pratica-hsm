package br.edu.utfpr.server;

import br.com.trueaccess.TacException;
import br.com.trueaccess.TacNDJavaLib;
import br.edu.utfpr.config.*;
import com.dinamonetworks.Dinamo;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Server {
    public static void main(String[] args) throws Exception {

        ServerConfig serverConfig = FileLoader.loadJsonFromConfig("server.json", ServerConfig.class);
        KeyConfig keyConfig = FileLoader.loadJsonFromConfig("server-keys.json", KeyConfig.class);
        AuthConfig config = FileLoader.loadJsonFromConfig("auth.json", AuthConfig.class);
        SecretCommonsConfig secretCommonsConfig =
                FileLoader.loadJsonFromConfig("secret-commons.json", SecretCommonsConfig.class);

        HttpServerFacade server = new HttpServerFacade(serverConfig.port());
        Dinamo dinamo = new Dinamo();
        dinamo.openSession(config.host(), config.user(), config.password());
        deleteKeysIfExists(dinamo, keyConfig);

        String document = FileLoader.loadDocumentFromConfig("document.txt");

        byte[] saltBytes = dinamo.generateHash(
                TacNDJavaLib.ALG_SHA3_512,
                secretCommonsConfig.saltSeed().getBytes(StandardCharsets.UTF_8));

        byte[] ivBytes = dinamo.generateHash(
                TacNDJavaLib.ALG_MD5,
                secretCommonsConfig.ivSeed().getBytes(StandardCharsets.UTF_8));

        server.addPostHandler("/generate-kex", clientPubKey -> {
            try {
                dinamo.deleteKeyIfExists(keyConfig.firstKeyPairId());
                dinamo.deleteKeyIfExists(keyConfig.sharedSecretKeyId());
                dinamo.createKey(keyConfig.firstKeyPairId(), TacNDJavaLib.ALG_ECC_SECP256K1);
                byte[] serverPubKey = dinamo.exportKey(keyConfig.firstKeyPairId(), TacNDJavaLib.PUBLICKEY_BLOB);
                dinamo.genEcdhKeyX963Sha256(
                        keyConfig.firstKeyPairId(),
                        keyConfig.sharedSecretKeyId(),
                        TacNDJavaLib.ALG_AES_256,
                        true,
                        false,
                        clientPubKey,
                        saltBytes);
                return serverPubKey;
            } catch (TacException e) {
                throw new RuntimeException(e);
            }
        });

        server.addGetHandler("/request-doc", () -> {
            try {
                return dinamo.encrypt(
                        keyConfig.sharedSecretKeyId(),
                        document.getBytes(StandardCharsets.UTF_8),
                        ivBytes,
                        TacNDJavaLib.D_PKCS5_PADDING,
                        TacNDJavaLib.MODE_CBC);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        server.addPostHandler("/verify-sign", (doc, params) -> {
            try {
                String encodedSignature = params.get("signature");
                byte[] decodedSignature = Base64.getDecoder().decode(encodedSignature);

                String encodedPublicKey = params.get("publicKey");
                byte[] decodedPublicKey = Base64.getDecoder().decode(encodedPublicKey);

                dinamo.deleteKeyIfExists(keyConfig.secondKeyPairId());
                dinamo.importKey(
                        keyConfig.secondKeyPairId(),
                        TacNDJavaLib.PUBLICKEY_BLOB_HSM,
                        TacNDJavaLib.ALG_OBJ_PUBKEY_RSA_BLOB,
                        decodedPublicKey,
                        false);

                byte[] clientPubKeyHandle = dinamo.getKeyHandle(keyConfig.secondKeyPairId());

                dinamo.verifySignature(clientPubKeyHandle, TacNDJavaLib.ALG_SHA2_256, decodedSignature, doc);

                return "Valid".getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                e.printStackTrace();
                return "Invalid".getBytes(StandardCharsets.UTF_8);
            }
        });

        server.addGetHandler("/shutdown", () -> {
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                    deleteKeysIfExists(dinamo, keyConfig);
                    dinamo.closeSession();
                    server.stop(3);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            return new byte[]{};
        });

        server.start();

        System.out.println("Server is running on port 8000");
    }

    private static void deleteKeysIfExists(Dinamo dinamo, KeyConfig keyConfig) throws Exception {
        dinamo.deleteKeyIfExists(keyConfig.firstKeyPairId());
        dinamo.deleteKeyIfExists(keyConfig.sharedSecretKeyId());
        dinamo.deleteKeyIfExists(keyConfig.secondKeyPairId());
    }

}
