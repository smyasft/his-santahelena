package co.santahelena.his.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import co.santahelena.his.util.ReglaException;

/**
 * Consulta el listado oficial "Código Único de Medicamentos Vigentes" que publica el INVIMA
 * en datos.gov.co (plataforma Socrata, API SODA). No requiere registro; el token es opcional.
 */
@Service
public class CumService {

    /** Un medicamento del listado oficial (una fila por CUM: expediente + consecutivo). */
    public static class Resultado {
        public String cum;
        public String expediente;
        public String consecutivo;
        public String producto;
        public String presentacion;
        public String formaFarmaceutica;
        public String via;
        public String titular;
        public String registroSanitario;
        public String atc;
        public String descripcionAtc;
        public String grupoSugerido;
        public boolean controlSugerido;
        public Set<String> principios = new LinkedHashSet<>();
        public boolean enInventario;

        public String principiosTexto() {
            return String.join(" + ", principios);
        }

        public String nombreSugerido() {
            String forma = formaFarmaceutica == null ? "" : " " + formaFarmaceutica.toLowerCase();
            return (producto == null ? "" : producto) + forma;
        }
    }

    private static final String CAMPOS = "expediente,consecutivocum,producto,descripcioncomercial,formafarmaceutica,viaadministracion,"
        + "titular,registrosanitario,atc,descripcionatc,principioactivo,cantidad,unidadmedida";
    private static final String VIGENTES = "estadoregistro='Vigente' AND estadocum='Activo' AND muestramedica='No'";

    private final String url;
    private final String token;
    private final RestClient http;

    public CumService(@Value("${his.cum.url}") String url, @Value("${his.cum.token:}") String token) {
        this.url = url;
        this.token = token;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(8000);
        f.setReadTimeout(15000);
        this.http = RestClient.builder().requestFactory(f).build();
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> consultar(Map<String, String> parametros) {
        StringBuilder q = new StringBuilder();
        for (Map.Entry<String, String> e : parametros.entrySet()) {
            q.append(q.length() == 0 ? "?" : "&").append(e.getKey()).append('=').append(enc(e.getValue()));
        }
        try {
            RestClient.RequestHeadersSpec<?> req = http.get().uri(URI.create(url + q)).header("Accept", "application/json");
            if (token != null && !token.isBlank()) {
                req = req.header("X-App-Token", token);
            }
            List<Map<String, Object>> filas = req.retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
            return filas == null ? List.of() : filas;
        } catch (RestClientException e) {
            throw new ReglaException("No fue posible consultar el listado del INVIMA en datos.gov.co (" + e.getClass().getSimpleName()
                + "). Revise la conexión a internet e intente de nuevo.");
        }
    }

    /** Busca por nombre comercial, principio activo, titular o expediente. */
    public List<Resultado> buscar(String texto) {
        if (texto == null || texto.trim().length() < 3) {
            throw new ReglaException("Escriba al menos 3 letras para buscar en el listado del INVIMA.");
        }
        Map<String, String> p = new LinkedHashMap<>();
        p.put("$select", CAMPOS);
        p.put("$q", texto.trim().replace("'", " "));
        p.put("$where", VIGENTES);
        p.put("$limit", "150");
        List<Resultado> r = agrupar(consultar(p));
        return r.size() > 30 ? r.subList(0, 30) : r;
    }

    /** Vuelve a consultar un CUM puntual (se usa al importar, para no confiar en datos del formulario). */
    public Resultado porCum(String expediente, String consecutivo) {
        if (expediente == null || consecutivo == null || !expediente.matches("\\d{1,12}") || !consecutivo.matches("\\d{1,4}")) {
            throw new ReglaException("Código CUM inválido.");
        }
        Map<String, String> p = new LinkedHashMap<>();
        p.put("$select", CAMPOS);
        p.put("$where", "expediente='" + expediente + "' AND consecutivocum='" + consecutivo + "' AND " + VIGENTES);
        p.put("$limit", "50");
        List<Resultado> r = agrupar(consultar(p));
        if (r.isEmpty()) {
            throw new ReglaException("El CUM " + expediente + "-" + consecutivo + " no aparece vigente en el listado del INVIMA.");
        }
        return r.get(0);
    }

    private static String t(Map<String, Object> fila, String campo) {
        Object v = fila.get(campo);
        return v == null ? null : v.toString().trim();
    }

    /** El dataset trae una fila por principio activo y por rol; se agrupan por CUM. */
    public static List<Resultado> agrupar(List<Map<String, Object>> filas) {
        Map<String, Resultado> m = new LinkedHashMap<>();
        for (Map<String, Object> f : filas) {
            String exp = t(f, "expediente");
            String con = t(f, "consecutivocum");
            if (exp == null || con == null) {
                continue;
            }
            String cum = exp + "-" + con;
            Resultado r = m.get(cum);
            if (r == null) {
                r = new Resultado();
                r.cum = cum;
                r.expediente = exp;
                r.consecutivo = con;
                r.producto = t(f, "producto");
                r.presentacion = t(f, "descripcioncomercial");
                r.formaFarmaceutica = t(f, "formafarmaceutica");
                r.via = t(f, "viaadministracion");
                r.titular = t(f, "titular");
                r.registroSanitario = t(f, "registrosanitario");
                r.atc = t(f, "atc");
                r.descripcionAtc = t(f, "descripcionatc");
                r.grupoSugerido = grupoPorAtc(r.atc);
                r.controlSugerido = controlPorAtc(r.atc);
                m.put(cum, r);
            }
            String pa = t(f, "principioactivo");
            if (pa != null) {
                String cant = t(f, "cantidad");
                String um = t(f, "unidadmedida");
                r.principios.add(pa + (cant != null ? " " + cant + (um != null ? " " + um : "") : ""));
            }
        }
        return new ArrayList<>(m.values());
    }

    private static final String[][] GRUPOS = {
        {"J01C", "PENICILINA"}, {"J01D", "CEFALOSPORINA"}, {"J01FA", "MACROLIDO"},
        {"M01A", "AINE"}, {"N02BA", "AINE"}, {"N02BE", "ANALGESICO"},
        {"N02A", "OPIOIDE"}, {"R05DA", "OPIOIDE"},
        {"N05BA", "BENZODIAZEPINA"}, {"N05CD", "BENZODIAZEPINA"}, {"N03AE", "BENZODIAZEPINA"},
        {"C09A", "IECA"}, {"C09B", "IECA"}, {"C09C", "ARA-II"}, {"C09D", "ARA-II"},
        {"C10AA", "ESTATINA"}, {"A10A", "INSULINA"}, {"A10B", "ANTIDIABETICO"},
        {"A02BC", "IBP"}, {"A03FA", "ANTIEMETICO"}, {"R03", "BRONCODILATADOR"}, {"B05", "LIQUIDOS"},
    };

    /** Sugiere el grupo farmacológico (que usa el motor de alertas) a partir del código ATC. */
    public static String grupoPorAtc(String atc) {
        if (atc == null) {
            return "OTRO";
        }
        String a = atc.trim().toUpperCase();
        String mejor = "OTRO";
        int largo = 0;
        for (String[] g : GRUPOS) {
            if (a.startsWith(g[0]) && g[0].length() > largo) {
                mejor = g[1];
                largo = g[0].length();
            }
        }
        return mejor;
    }

    /** Opioides, benzodiazepinas y estimulantes suelen ser de control especial; el farmaceuta confirma. */
    public static boolean controlPorAtc(String atc) {
        if (atc == null) {
            return false;
        }
        String a = atc.trim().toUpperCase();
        return a.startsWith("N02A") || a.startsWith("N05BA") || a.startsWith("N05CD") || a.startsWith("N03AE") || a.startsWith("N06BA");
    }

    public static List<String> gruposDisponibles() {
        Set<String> s = new LinkedHashSet<>();
        for (String[] g : GRUPOS) {
            s.add(g[1]);
        }
        s.add("OTRO");
        return new ArrayList<>(s);
    }
}
