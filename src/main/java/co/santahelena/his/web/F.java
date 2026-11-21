package co.santahelena.his.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.MultiValueMap;

import co.santahelena.his.util.Form;

/** Lectura de formularios con valores repetidos (sin la división por comas que hace Spring con List). */
public final class F {
    private F() {
    }

    public static String v(MultiValueMap<String, String> f, String k) {
        return Form.s(f.getFirst(k));
    }

    public static List<String> l(MultiValueMap<String, String> f, String k) {
        List<String> r = f.get(k);
        return r == null ? new ArrayList<>() : r;
    }

    public static String at(List<String> l, int i) {
        return i < l.size() ? Form.s(l.get(i)) : null;
    }
}
