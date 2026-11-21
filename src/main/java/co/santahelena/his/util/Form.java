package co.santahelena.his.util;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Conversión segura de parámetros de formulario (nunca lanza excepción por un campo mal escrito). */
public final class Form {

    private Form() {
    }

    public static String s(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    public static Integer i(String v) {
        String t = s(v);
        if (t == null) {
            return null;
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double d(String v) {
        String t = s(v);
        if (t == null) {
            return null;
        }
        try {
            return Double.valueOf(t.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate fecha(String v) {
        String t = s(v);
        if (t == null) {
            return null;
        }
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static LocalDateTime fechaHora(String v) {
        String t = s(v);
        if (t == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(t);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Minúsculas y sin tildes, para búsquedas y comparaciones. */
    public static String norm(String v) {
        if (v == null) {
            return "";
        }
        String n = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase().trim();
    }

    public static boolean si(String v) {
        return v != null && (v.equals("on") || v.equals("true") || v.equals("si"));
    }
}
