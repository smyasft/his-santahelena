package co.santahelena.his.util;

import java.util.HashMap;
import java.util.Map;

/** Genera códigos de barras Code 39 en SVG para las etiquetas de muestras de laboratorio. */
public final class Code39 {

    private static final Map<Character, String> PATRONES = new HashMap<>();

    static {
        String[][] t = {
            {"0", "nnnwwnwnn"}, {"1", "wnnwnnnnw"}, {"2", "nnwwnnnnw"}, {"3", "wnwwnnnnn"}, {"4", "nnnwwnnnw"},
            {"5", "wnnwwnnnn"}, {"6", "nnwwwnnnn"}, {"7", "nnnwnnwnw"}, {"8", "wnnwnnwnn"}, {"9", "nnwwnnwnn"},
            {"A", "wnnnnwnnw"}, {"B", "nnwnnwnnw"}, {"C", "wnwnnwnnn"}, {"D", "nnnnwwnnw"}, {"E", "wnnnwwnnn"},
            {"F", "nnwnwwnnn"}, {"G", "nnnnnwwnw"}, {"H", "wnnnnwwnn"}, {"I", "nnwnnwwnn"}, {"J", "nnnnwwwnn"},
            {"K", "wnnnnnnww"}, {"L", "nnwnnnnww"}, {"M", "wnwnnnnwn"}, {"N", "nnnnwnnww"}, {"O", "wnnnwnnwn"},
            {"P", "nnwnwnnwn"}, {"Q", "nnnnnnwww"}, {"R", "wnnnnnwwn"}, {"S", "nnwnnnwwn"}, {"T", "nnnnwnwwn"},
            {"U", "wwnnnnnnw"}, {"V", "nwwnnnnnw"}, {"W", "wwwnnnnnn"}, {"X", "nwnnwnnnw"}, {"Y", "wwnnwnnnn"},
            {"Z", "nwwnwnnnn"}, {"-", "nwnnnnwnw"}, {".", "wwnnnnwnn"}, {" ", "nwwnnnwnn"}, {"*", "nwnnwnwnn"}
        };
        for (String[] p : t) {
            PATRONES.put(p[0].charAt(0), p[1]);
        }
    }

    private Code39() {
    }

    public static String svg(String codigo, int alto) {
        String texto = "*" + codigo.toUpperCase() + "*";
        StringBuilder barras = new StringBuilder();
        int x = 0;
        int angosto = 2;
        int ancho = 5;
        for (char c : texto.toCharArray()) {
            String p = PATRONES.get(c);
            if (p == null) {
                continue;
            }
            for (int i = 0; i < p.length(); i++) {
                int w = p.charAt(i) == 'w' ? ancho : angosto;
                if (i % 2 == 0) {
                    barras.append("<rect x=\"").append(x).append("\" y=\"0\" width=\"").append(w)
                            .append("\" height=\"").append(alto).append("\" fill=\"#000\"/>");
                }
                x += w;
            }
            x += angosto;
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + x + " " + alto
                + "\" width=\"" + x + "\" height=\"" + alto + "\" role=\"img\" aria-label=\"" + codigo + "\">"
                + barras + "</svg>";
    }
}
