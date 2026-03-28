package com.pronosticup.backend.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Service
public class EncryptionService {

    @Value("${app.security.pronostic-encryption-key}")
    private String secretKey;

    private static final String ALGORITHM = "AES";

    /**
     * Encripto un texto plano usando AES y lo devuelvo en Base64.
     */
    public String encrypt(String data) {
        try {
            SecretKeySpec key = buildKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] encrypted = cipher.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception e) {
            throw new RuntimeException("Error encriptando pronóstico", e);
        }
    }

    /**
     * Desencripto un texto en Base64 usando AES.
     */
    public String decrypt(String encryptedData) {
        try {
            SecretKeySpec key = buildKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);

            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            return new String(cipher.doFinal(decoded));

        } catch (Exception e) {
            throw new RuntimeException("Error desencriptando pronóstico", e);
        }
    }

    /**
     * Construyo la clave AES a partir del valor configurado.
     */
    private SecretKeySpec buildKey() {
        byte[] keyBytes = new byte[16];
        byte[] providedKey = secretKey.getBytes();

        System.arraycopy(providedKey, 0, keyBytes, 0,
                Math.min(providedKey.length, keyBytes.length));

        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
