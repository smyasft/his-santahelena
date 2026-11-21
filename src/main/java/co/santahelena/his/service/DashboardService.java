package co.santahelena.his.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import co.santahelena.his.model.Admision;
import co.santahelena.his.model.Cama;
import co.santahelena.his.model.Cita;

/** Datos para las gráficas del tablero de inicio (donas, barras, calendario y agenda del día). */
@Service
public class DashboardService {

    public static class Segmento {
        public final String etiqueta;
        public final long valor;
        public final String color;
        public final int pct;

        Segmento(String etiqueta, long valor, String color, int pct) {
            this.etiqueta = etiqueta;
            this.valor = valor;
            this.color = color;
            this.pct = pct;
        }
    }

    /** Gráfica de dona: el CSS la dibuja con conic-gradient. */
    public static class Dona {
        public final String titulo;
        public final List<Segmento> segmentos = new ArrayList<>();
        public long total;
        public String centro;
        public String subcentro;

        Dona(String titulo) {
            this.titulo = titulo;
        }

        public String gradiente() {
            if (total == 0) {
                return "conic-gradient(#e3e8ef 0 100%)";
            }
            StringBuilder sb = new StringBuilder("conic-gradient(");
            double acum = 0;
            for (int i = 0; i < segmentos.size(); i++) {
                Segmento s = segmentos.get(i);
                double desde = acum;
                acum += s.valor * 100.0 / total;
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(s.color).append(' ').append(String.format(Locale.US, "%.2f", desde)).append("% ")
                    .append(String.format(Locale.US, "%.2f", acum)).append('%');
            }
            return sb.append(')').toString();
        }
    }

    public static class Barra {
        public final String etiqueta;
        public final String fecha;
        public final long valor;
        public final int alto;
        public final boolean hoy;

        Barra(String etiqueta, String fecha, long valor, int alto, boolean hoy) {
            this.etiqueta = etiqueta;
            this.fecha = fecha;
            this.valor = valor;
            this.alto = alto;
            this.hoy = hoy;
        }
    }

    public static class Dia {
        public final int numero;
        public final boolean hoy;
        public final boolean conCitas;

        Dia(int numero, boolean hoy, boolean conCitas) {
            this.numero = numero;
            this.hoy = hoy;
            this.conCitas = conCitas;
        }
    }

    public static class Mes {
        public String titulo;
        public final List<List<Dia>> semanas = new ArrayList<>();
    }

    private final MongoTemplate mongo;

    public DashboardService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    private long contar(Criteria c, Class<?> tipo) {
        return mongo.count(Query.query(c), tipo);
    }

    private static int pct(long v, long total) {
        return total == 0 ? 0 : (int) Math.round(v * 100.0 / total);
    }

    public Dona camas() {
        Dona d = new Dona("Ocupación de camas");
        String[][] estados = {{"OCUPADA", "Ocupadas", "#7c3aed"}, {"LIBRE", "Libres", "#14b8a6"}, {"LIMPIEZA", "En limpieza", "#f59e0b"}, {"BLOQUEADA", "Bloqueadas", "#ef4444"}};
        long[] v = new long[estados.length];
        for (int i = 0; i < estados.length; i++) {
            v[i] = contar(Criteria.where("estado").is(estados[i][0]), Cama.class);
            d.total += v[i];
        }
        for (int i = 0; i < estados.length; i++) {
            d.segmentos.add(new Segmento(estados[i][1], v[i], estados[i][2], pct(v[i], d.total)));
        }
        d.centro = pct(v[0], d.total) + "%";
        d.subcentro = "ocupación";
        return d;
    }

    public Dona triaje() {
        Dona d = new Dona("Urgencias por nivel de triaje");
        String[] nombres = {"Sin clasificar", "I · Resucitación", "II · Emergencia", "III · Urgencia", "IV · Menor", "V · No urgente"};
        String[] colores = {"#94a3b8", "#dc2626", "#f97316", "#facc15", "#22c55e", "#3b82f6"};
        long[] v = new long[6];
        Query q = Query.query(Criteria.where("tipo").is("URGENCIAS").and("estado").in("EN_ESPERA_TRIAJE", "TRIADO", "EN_ATENCION"));
        for (Admision a : mongo.find(q, Admision.class)) {
            int n = a.triajeNivel == null ? 0 : Math.max(1, Math.min(5, a.triajeNivel));
            v[n]++;
            d.total++;
        }
        for (int i = 1; i <= 5; i++) {
            d.segmentos.add(new Segmento(nombres[i], v[i], colores[i], pct(v[i], d.total)));
        }
        d.segmentos.add(new Segmento(nombres[0], v[0], colores[0], pct(v[0], d.total)));
        d.centro = String.valueOf(d.total);
        d.subcentro = d.total == 1 ? "paciente" : "pacientes";
        return d;
    }

    /** Citas no canceladas por día de la semana actual (lunes a domingo). */
    public List<Barra> citasSemana(String profesionalId) {
        LocalDate hoy = LocalDate.now();
        LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
        String[] dias = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
        long[] v = new long[7];
        long max = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = lunes.plusDays(i);
            Criteria c = Criteria.where("fechaHora").gte(d.atStartOfDay()).lt(d.plusDays(1).atStartOfDay()).and("estado").ne("CANCELADA");
            if (profesionalId != null) {
                c = c.and("profesionalId").is(profesionalId);
            }
            v[i] = contar(c, Cita.class);
            max = Math.max(max, v[i]);
        }
        List<Barra> r = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = lunes.plusDays(i);
            int alto = max == 0 ? 4 : Math.max(4, (int) Math.round(v[i] * 100.0 / max));
            r.add(new Barra(dias[i], String.valueOf(d.getDayOfMonth()), v[i], alto, d.equals(hoy)));
        }
        return r;
    }

    public Mes calendario(String profesionalId) {
        LocalDate hoy = LocalDate.now();
        YearMonth ym = YearMonth.from(hoy);
        String[] meses = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
        Mes m = new Mes();
        m.titulo = meses[ym.getMonthValue() - 1] + " " + ym.getYear();
        Criteria c = Criteria.where("fechaHora").gte(ym.atDay(1).atStartOfDay()).lt(ym.plusMonths(1).atDay(1).atStartOfDay()).and("estado").ne("CANCELADA");
        if (profesionalId != null) {
            c = c.and("profesionalId").is(profesionalId);
        }
        Set<Integer> conCitas = new HashSet<>();
        for (Cita ci : mongo.find(Query.query(c), Cita.class)) {
            conCitas.add(ci.fechaHora.getDayOfMonth());
        }
        List<Dia> semana = new ArrayList<>();
        int vacios = ym.atDay(1).getDayOfWeek().getValue() - 1;
        for (int i = 0; i < vacios; i++) {
            semana.add(null);
        }
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            semana.add(new Dia(d, d == hoy.getDayOfMonth(), conCitas.contains(d)));
            if (semana.size() == 7) {
                m.semanas.add(semana);
                semana = new ArrayList<>();
            }
        }
        if (!semana.isEmpty()) {
            while (semana.size() < 7) {
                semana.add(null);
            }
            m.semanas.add(semana);
        }
        return m;
    }

    /** Citas de hoy en orden de hora (las del profesional si se indica). */
    public List<Cita> agendaHoy(String profesionalId, int limite) {
        LocalDate hoy = LocalDate.now();
        Criteria c = Criteria.where("fechaHora").gte(hoy.atStartOfDay()).lt(hoy.plusDays(1).atStartOfDay());
        if (profesionalId != null) {
            c = c.and("profesionalId").is(profesionalId);
        }
        return mongo.find(Query.query(c).with(Sort.by("fechaHora")).limit(limite), Cita.class);
    }

    public static String iniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "?";
        }
        String[] p = nombre.trim().split("\\s+");
        return (p[0].substring(0, 1) + (p.length > 1 ? p[p.length > 2 ? 2 : 1].substring(0, 1) : "")).toUpperCase();
    }

    public static boolean pasada(LocalDateTime t) {
        return t != null && t.isBefore(LocalDateTime.now());
    }
}
