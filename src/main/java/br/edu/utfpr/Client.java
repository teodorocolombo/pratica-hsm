package br.edu.utfpr;

import br.com.trueaccess.TacNDJavaLib;
import com.dinamonetworks.Dinamo;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class Client {

    public static void main(String[] args) throws Exception {
        HttpClientFacade httpClient = new HttpClientFacade();
        ServerConfig serverConfig = JsonFileLoader.loadFromConfig("server.json", ServerConfig.class);
        AuthConfig config = JsonFileLoader.loadFromConfig("auth.json", AuthConfig.class);

        Dinamo dinamo = new Dinamo();
        dinamo.openSession(config.host(), config.user(), config.password());
        String url = serverConfig.getUrl();

        String keyId = "meu_key_id";
        String secondKeyId = "outro_meu_key_id";
        String sharedKeyId = "minha_shared_key_id";
        dinamo.deleteKeyIfExists(keyId);
        dinamo.deleteKeyIfExists(sharedKeyId);
        dinamo.deleteKeyIfExists(secondKeyId);

        dinamo.createKey(keyId, TacNDJavaLib.ALG_ECC_SECP256K1);
        byte[] clientPubKey = dinamo.exportKey(keyId, TacNDJavaLib.PUBLICKEY_BLOB);

        byte[] serverPubKey = httpClient.post(url + "/generate-kex", clientPubKey);
        System.out.println("serverPubKey: " + Arrays.toString(serverPubKey));

        dinamo.genEcdhKeyX963Sha256(
                keyId, sharedKeyId, TacNDJavaLib.ALG_AES_256, true, false, serverPubKey, new byte[]{});


        byte[] cipherText = httpClient.get(url + "/request-doc");

        byte[] decrypted = dinamo.decrypt(sharedKeyId, cipherText);

        String doc = new String(decrypted, StandardCharsets.UTF_8);
        System.out.println(doc);


        dinamo.createKey(secondKeyId, TacNDJavaLib.ALG_ECC_SECP256K1);
        byte[] secondPublicKey = dinamo.exportKey(secondKeyId, TacNDJavaLib.PUBLICKEY_BLOB);


        String encodedPublicKey = Base64.getEncoder().encodeToString(secondPublicKey);
        byte[] signature = dinamo.sign(secondKeyId, TacNDJavaLib.ALG_SHA3_256, decrypted);
        String encodedSignature = Base64.getEncoder().encodeToString(signature);

        byte[] isValidBytes = httpClient.post(
                url + "/verify-sign?signature=" + encodedSignature + "&publicKey=" + encodedPublicKey,
                clientPubKey);

        String isValid = new String(isValidBytes, StandardCharsets.UTF_8);
        System.out.println("Is Valid? " + isValid);

        dinamo.deleteKeyIfExists(keyId);
        dinamo.deleteKeyIfExists(sharedKeyId);
        dinamo.deleteKeyIfExists(secondKeyId);
        dinamo.closeSession();
    }
}