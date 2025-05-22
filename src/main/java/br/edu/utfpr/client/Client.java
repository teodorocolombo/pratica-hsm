package br.edu.utfpr.client;

import br.com.trueaccess.TacNDJavaLib;
import br.edu.utfpr.config.*;
import com.dinamonetworks.Dinamo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Client {


    public static void main(String[] args) throws Exception {

        ServerConfig serverConfig = FileLoader.loadJsonFromConfig("server.json", ServerConfig.class);
        KeyConfig keyConfig = FileLoader.loadJsonFromConfig("client-keys.json", KeyConfig.class);
        AuthConfig config = FileLoader.loadJsonFromConfig("auth.json", AuthConfig.class);
        SecretCommonsConfig secretCommonsConfig =
                FileLoader.loadJsonFromConfig("secret-commons.json", SecretCommonsConfig.class);

        Dinamo dinamo = new Dinamo();
        dinamo.openSession(config.host(), config.user(), config.password());
        deleteKeysIfExists(dinamo, keyConfig);

        byte[] saltBytes = dinamo.generateHash(
                TacNDJavaLib.ALG_SHA3_512,
                secretCommonsConfig.saltSeed().getBytes(StandardCharsets.UTF_8));

        byte[] ivBytes = dinamo.generateHash(
                TacNDJavaLib.ALG_MD5,
                secretCommonsConfig.ivSeed().getBytes(StandardCharsets.UTF_8));

        String url = serverConfig.getUrl();


        try {
            HttpClientFacade httpClient = new HttpClientFacade(keyConfig.certificatePath());
            dinamo.createKey(keyConfig.firstKeyPairId(), TacNDJavaLib.ALG_ECC_SECP256K1);
            byte[] clientPubKey = dinamo.exportKey(keyConfig.firstKeyPairId(), TacNDJavaLib.PUBLICKEY_BLOB);

            System.out.println("Requesting KEX from server...");
            byte[] serverPubKey = httpClient.post(url + "/generate-kex", clientPubKey);
            dinamo.genEcdhKeyX963Sha256(
                    keyConfig.firstKeyPairId(),
                    keyConfig.sharedSecretKeyId(),
                    TacNDJavaLib.ALG_AES_256,
                    true,
                    false,
                    serverPubKey,
                    saltBytes);

            System.out.println("Requesting encrypted document from server...");
            byte[] encryptedDocument = httpClient.get(url + "/request-doc");

            System.out.println("Decrypting received document...");
            byte[] decryptedDocument = dinamo.decrypt(
                    keyConfig.sharedSecretKeyId(),
                    encryptedDocument,
                    ivBytes,
                    TacNDJavaLib.D_PKCS5_PADDING,
                    TacNDJavaLib.MODE_CBC);

            String doc = new String(decryptedDocument, StandardCharsets.UTF_8);
            System.out.println("Decrypted document: " + doc);

            dinamo.createKey(keyConfig.secondKeyPairId(), TacNDJavaLib.ALG_RSA_2048);
            byte[] secondPublicKey = dinamo.exportKey(keyConfig.secondKeyPairId(), TacNDJavaLib.PUBLICKEY_BLOB);

            System.out.println("Signing document...");
            byte[] signature = dinamo.sign(keyConfig.secondKeyPairId(), TacNDJavaLib.ALG_SHA2_256, decryptedDocument);

            System.out.println("Verifying document signature...");
            String verifySignUrl = String.format("%s/verify-sign?signature=%s&publicKey=%s", url,
                    toBase64Encoded(signature), toBase64Encoded(secondPublicKey));

            byte[] isValidBytes = httpClient.post(verifySignUrl, doc.getBytes(StandardCharsets.UTF_8));
            String isValid = new String(isValidBytes, StandardCharsets.UTF_8);
            System.out.println("Is signature valid? " + isValid);

            httpClient.get(url + "/shutdown");

        } finally {
            deleteKeysIfExists(dinamo, keyConfig);
            dinamo.closeSession();
        }
    }

    private static void deleteKeysIfExists(Dinamo dinamo, KeyConfig keyConfig) throws Exception {
        dinamo.deleteKeyIfExists(keyConfig.firstKeyPairId());
        dinamo.deleteKeyIfExists(keyConfig.sharedSecretKeyId());
        dinamo.deleteKeyIfExists(keyConfig.secondKeyPairId());
    }

    private static String toBase64Encoded(byte[] data) {
        return URLEncoder.encode(Base64.getEncoder().encodeToString(data), StandardCharsets.UTF_8);
    }

}