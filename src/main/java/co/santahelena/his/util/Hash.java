package co.santahelena.his.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Huellas criptográficas: firma de registros clínicos (SHA-256) y CUFE de facturas (SHA-384). */
public final class Hash {

    private Hash() {
    }

    public static String sha256(String texto) {
        return hex("SHA-256", texto);
    }

    public static String sha384(String texto) {
        return hex("SHA-384", texto);
    }

    private static String hex(String algoritmo, String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance(algoritmo);
            byte[] bytes = md.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
