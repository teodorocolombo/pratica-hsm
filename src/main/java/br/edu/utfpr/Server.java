package br.edu.utfpr;

import br.com.trueaccess.TacException;
import br.com.trueaccess.TacNDJavaLib;
import com.dinamonetworks.Dinamo;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class Server {
    public static void main(String[] args) throws Exception {
        ServerConfig serverConfig = JsonFileLoader.loadFromConfig("server.json", ServerConfig.class);
        HttpServerFacade server = new HttpServerFacade(serverConfig.port());

        AuthConfig config = JsonFileLoader.loadFromConfig("auth.json", AuthConfig.class);
        Dinamo dinamo = new Dinamo();
        dinamo.openSession(config.host(), config.user(), config.password());

        String privateKeyId = "teo_server_key_id";
        String sharedSecretKeyId = "teo_server_secret_key_id";

        dinamo.deleteKeyIfExists(privateKeyId);
        dinamo.deleteKeyIfExists(sharedSecretKeyId);

        server.addPostHandler("/generate-kex", clientPubKey -> {
            try {
                dinamo.createKey(privateKeyId, TacNDJavaLib.ALG_ECC_SECP256K1);
                byte[] serverPubKey = dinamo.exportKey(privateKeyId, TacNDJavaLib.PUBLICKEY_BLOB);
                System.out.println("serverPubKey: " + Arrays.toString(serverPubKey));
                dinamo.genEcdhKeyX963Sha256(
                        privateKeyId, sharedSecretKeyId, TacNDJavaLib.ALG_AES_256, true, false, serverPubKey, new byte[]{});
                return serverPubKey;
            } catch (TacException e) {
                throw new RuntimeException(e);
            }
        });

        server.addGetHandler("/request-doc", () -> {
            try {
                String message = "Essa eh a mensagem";
                return dinamo.encrypt(sharedSecretKeyId, message.getBytes(StandardCharsets.UTF_8));
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

                dinamo.verifySignature(decodedPublicKey, TacNDJavaLib.ALG_SHA3_256, decodedSignature, doc);

                return new byte[]{1};
            } catch (Exception e) {
                return new byte[]{0};
            }
        });

        server.start();

        System.out.println("Server is running on port 8000");
    }
}
