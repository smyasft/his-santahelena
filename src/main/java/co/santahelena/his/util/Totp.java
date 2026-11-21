package co.santahelena.his.util;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Códigos de un solo uso (TOTP, RFC 6238) compatibles con Google Authenticator y Microsoft Authenticator. */
public final class Totp {

    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    public static String nuevoSecreto() {
        byte[] b = new byte[20];
        RANDOM.nextBytes(b);
        return base32(b);
    }

    public static String base32(byte[] datos) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte x : datos) {
            buffer = (buffer << 8) | (x & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALFABETO.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(ALFABETO.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    public static byte[] decodificar(String texto) {
        String t = texto.replace(" ", "").replace("=", "").toUpperCase();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : t.toCharArray()) {
            int v = ALFABETO.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Secreto Base32 inválido");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    public static String codigo(String secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificar(secreto), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            int o = h[h.length - 1] & 0xf;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Acepta el código actual y el de 30 s antes o después (desfase de reloj del celular). */
    public static boolean verificar(String secreto, String codigo) {
        if (secreto == null || codigo == null) {
            return false;
        }
        String c = codigo.replace(" ", "").trim();
        if (!c.matches("\\d{6}")) {
            return false;
        }
        long paso = System.currentTimeMillis() / 1000 / 30;
        for (long d = -1; d <= 1; d++) {
            if (codigo(secreto, paso + d).equals(c)) {
                return true;
            }
        }
        return false;
    }

    public static String uri(String secreto, String usuario) {
        return "otpauth://totp/HIS%20Santa%20Helena:" + URLEncoder.encode(usuario, StandardCharsets.UTF_8)
                + "?secret=" + secreto + "&issuer=HIS%20Santa%20Helena&digits=6&period=30";
    }

    /** Secreto en grupos de 4 para escribirlo a mano en la app. */
    public static String agrupado(String secreto) {
        return secreto.replaceAll("(.{4})(?!$)", "$1 ");
    }
}
