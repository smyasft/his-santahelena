package co.santahelena.his.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import co.santahelena.his.model.Cama;
import co.santahelena.his.model.Configuracion;
import co.santahelena.his.model.Lote;
import co.santahelena.his.model.Medicamento;
import co.santahelena.his.model.SignosVitales;

/** Reglas de negocio puras (sin base de datos), fáciles de probar. */
public final class Reglas {

    private Reglas() {
    }

    // ------------------------------------------------------------------ Triaje (Res. 5596 de 2015)

    /**
     * Sugiere el nivel de triaje (1 = más grave, 5 = menos grave) a partir de los signos vitales.
     * Es una ayuda: la clasificación final la decide el profesional de enfermería.
     */
    public static int sugerirNivel(SignosVitales s) {
        if (s == null) {
            return 5;
        }
        if (menor(s.saturacion, 85) || menor(s.taSistolica, 80) || mayor(s.fc, 150) || menor(s.fc, 40)
                || mayor(s.fr, 35) || menor(s.fr, 8) || menor(s.glucometria, 50)) {
            return 1;
        }
        if (menor(s.saturacion, 90) || mayorIgual(s.taSistolica, 180) || mayorIgual(s.taDiastolica, 120)
                || mayor(s.fc, 120) || mayor(s.fr, 28) || mayorIgual(s.temperatura, 40.0) || mayor(s.glucometria, 400)) {
            return 2;
        }
        if (menor(s.saturacion, 94) || mayorIgual(s.taSistolica, 160) || mayorIgual(s.taDiastolica, 100)
                || mayor(s.fc, 100) || mayor(s.fr, 22) || mayorIgual(s.temperatura, 38.5) || mayor(s.glucometria, 250)) {
            return 3;
        }
        if (mayorIgual(s.temperatura, 37.8) || mayorIgual(s.taSistolica, 140) || mayor(s.fc, 90)) {
            return 4;
        }
        return 5;
    }

    public static String nombreNivel(Integer nivel) {
        if (nivel == null) {
            return "Sin clasificar";
        }
        return switch (nivel) {
            case 1 -> "Triaje I · Atención inmediata";
            case 2 -> "Triaje II · Hasta 30 minutos";
            case 3 -> "Triaje III · Urgencia";
            case 4 -> "Triaje IV · Prioritaria";
            default -> "Triaje V · No urgente";
        };
    }

    /** Devuelve un mensaje si algún signo vital es físicamente imposible (error de digitación). */
    public static String valorImposible(SignosVitales s) {
        if (fuera(s.saturacion, 0, 100)) {
            return "La saturación de oxígeno debe estar entre 0 y 100 %.";
        }
        if (s.temperatura != null && (s.temperatura < 25 || s.temperatura > 45)) {
            return "La temperatura debe estar entre 25 y 45 °C.";
        }
        if (fuera(s.fc, 0, 300)) {
            return "La frecuencia cardiaca debe estar entre 0 y 300 lpm.";
        }
        if (fuera(s.fr, 0, 80)) {
            return "La frecuencia respiratoria debe estar entre 0 y 80 rpm.";
        }
        if (fuera(s.taSistolica, 30, 300) || fuera(s.taDiastolica, 10, 200)) {
            return "La tensión arterial está fuera de los valores posibles.";
        }
        if (s.taSistolica != null && s.taDiastolica != null && s.taDiastolica >= s.taSistolica) {
            return "La tensión diastólica debe ser menor que la sistólica.";
        }
        if (fuera(s.glucometria, 10, 1000)) {
            return "La glucometría debe estar entre 10 y 1000 mg/dL.";
        }
        return null;
    }

    /** Alertas de signos vitales fuera de los rangos configurados por el administrador (US-018). */
    public static List<String> alertasSignos(SignosVitales s, Configuracion c) {
        List<String> a = new ArrayList<>();
        if (s.taSistolica != null && (s.taSistolica < c.taSistolicaMin || s.taSistolica > c.taSistolicaMax)) {
            a.add("Tensión sistólica " + s.taSistolica + " mmHg (rango " + c.taSistolicaMin + "–" + c.taSistolicaMax + ")");
        }
        if (s.taDiastolica != null && (s.taDiastolica < c.taDiastolicaMin || s.taDiastolica > c.taDiastolicaMax)) {
            a.add("Tensión diastólica " + s.taDiastolica + " mmHg (rango " + c.taDiastolicaMin + "–" + c.taDiastolicaMax + ")");
        }
        if (s.fc != null && (s.fc < c.fcMin || s.fc > c.fcMax)) {
            a.add("Frecuencia cardiaca " + s.fc + " lpm (rango " + c.fcMin + "–" + c.fcMax + ")");
        }
        if (s.fr != null && (s.fr < c.frMin || s.fr > c.frMax)) {
            a.add("Frecuencia respiratoria " + s.fr + " rpm (rango " + c.frMin + "–" + c.frMax + ")");
        }
        if (s.temperatura != null && (s.temperatura < c.temperaturaMin || s.temperatura > c.temperaturaMax)) {
            a.add("Temperatura " + s.temperatura + " °C (rango " + c.temperaturaMin + "–" + c.temperaturaMax + ")");
        }
        if (s.saturacion != null && s.saturacion < c.saturacionMin) {
            a.add("Saturación " + s.saturacion + " % (mínimo " + c.saturacionMin + ")");
        }
        if (s.glucometria != null && (s.glucometria < c.glucometriaMin || s.glucometria > c.glucometriaMax)) {
            a.add("Glucometría " + s.glucometria + " mg/dL (rango " + c.glucometriaMin + "–" + c.glucometriaMax + ")");
        }
        return a;
    }

    // ------------------------------------------------------------------ Citas (RN-09)

    public static boolean puedeCancelarPorPortal(LocalDateTime cita, LocalDateTime ahora) {
        return !cita.isBefore(ahora.plusHours(24));
    }

    // ------------------------------------------------------------------ Camas (RN-11)

    /** Devuelve el motivo por el que la cama no sirve para el paciente, o null si es compatible. */
    public static String incompatibilidadCama(Cama cama, String sexoPaciente, boolean requiereAislamiento, String servicio) {
        if (!"LIBRE".equals(cama.estado)) {
            return "La cama no está libre (" + cama.estado.toLowerCase() + ").";
        }
        if (cama.sexoPermitido != null && sexoPaciente != null && !cama.sexoPermitido.equals(sexoPaciente)) {
            return "Habitación exclusiva para pacientes de sexo " + ("M".equals(cama.sexoPermitido) ? "masculino" : "femenino") + ".";
        }
        if (requiereAislamiento && !cama.aislamiento) {
            return "El paciente requiere una cama de aislamiento.";
        }
        if (!requiereAislamiento && cama.aislamiento) {
            return "Cama reservada para pacientes en aislamiento.";
        }
        if (servicio != null && cama.servicio != null && !Form.norm(servicio).equals(Form.norm(cama.servicio))) {
            return "La cama pertenece al servicio de " + cama.servicio + ".";
        }
        return null;
    }

    // ------------------------------------------------------------------ Farmacia (RN-01, RN-15)

    public static class Asignacion {
        public final Lote lote;
        public final int cantidad;

        public Asignacion(Lote lote, int cantidad) {
            this.lote = lote;
            this.cantidad = cantidad;
        }
    }

    /**
     * Elige los lotes a descontar en orden de vencimiento (FEFO) ignorando los vencidos.
     * Devuelve null si las existencias vigentes no alcanzan.
     */
    public static List<Asignacion> fefo(List<Lote> lotes, int cantidad, LocalDate hoy) {
        List<Lote> vigentes = new ArrayList<>();
        for (Lote l : lotes) {
            if (l.cantidad > 0 && l.vence != null && !l.vence.isBefore(hoy)) {
                vigentes.add(l);
            }
        }
        vigentes.sort((a, b) -> a.vence.compareTo(b.vence));
        List<Asignacion> r = new ArrayList<>();
        int falta = cantidad;
        for (Lote l : vigentes) {
            if (falta == 0) {
                break;
            }
            int toma = Math.min(falta, l.cantidad);
            r.add(new Asignacion(l, toma));
            falta -= toma;
        }
        return falta == 0 ? r : null;
    }

    public static int stockVigente(List<Lote> lotes, LocalDate hoy) {
        int t = 0;
        for (Lote l : lotes) {
            if (l.vence != null && !l.vence.isBefore(hoy)) {
                t += l.cantidad;
            }
        }
        return t;
    }

    // ------------------------------------------------------------------ Prescripción (RN-05)

    private static final String[][] INTERACCIONES = {
        {"IECA", "ARA-II", "Doble bloqueo del sistema renina-angiotensina: riesgo de hiperpotasemia y falla renal."},
        {"AINE", "AINE", "Dos antiinflamatorios no esteroideos: riesgo de sangrado digestivo y daño renal."},
        {"AINE", "IECA", "AINE con IECA: riesgo de falla renal y menor efecto antihipertensivo."},
        {"AINE", "ARA-II", "AINE con ARA-II: riesgo de falla renal y menor efecto antihipertensivo."},
        {"OPIOIDE", "OPIOIDE", "Dos opioides al tiempo: riesgo de depresión respiratoria."},
        {"OPIOIDE", "BENZODIAZEPINA", "Opioide con benzodiacepina: riesgo grave de depresión respiratoria."}
    };

    /**
     * Revisa alergias del paciente e interacciones entre los medicamentos nuevos y los que ya tiene activos.
     */
    public static List<String> alertasPrescripcion(List<Medicamento> nuevos, List<Medicamento> activos, List<String> alergias) {
        List<String> r = new ArrayList<>();
        for (Medicamento m : nuevos) {
            for (String alergia : alergias) {
                String a = Form.norm(alergia);
                if (a.isEmpty()) {
                    continue;
                }
                String nombre = Form.norm(m.nombre);
                String grupo = Form.norm(m.grupo);
                if (nombre.contains(a) || (!grupo.isEmpty() && (grupo.contains(a) || a.contains(grupo)))) {
                    r.add("ALERGIA: el paciente es alérgico a " + alergia + " y se prescribe " + m.nombre + ".");
                }
            }
        }
        List<Medicamento> todos = new ArrayList<>(nuevos);
        todos.addAll(activos);
        for (int i = 0; i < nuevos.size(); i++) {
            for (int j = 0; j < todos.size(); j++) {
                if (j < nuevos.size() && j <= i) {
                    continue;
                }
                Medicamento a = nuevos.get(i);
                Medicamento b = todos.get(j);
                if (a.id != null && a.id.equals(b.id) && j >= nuevos.size()) {
                    r.add("DUPLICIDAD: " + a.nombre + " ya está en una fórmula activa del paciente.");
                    continue;
                }
                for (String[] in : INTERACCIONES) {
                    boolean coincide = (in[0].equals(a.grupo) && in[1].equals(b.grupo)) || (in[1].equals(a.grupo) && in[0].equals(b.grupo));
                    if (coincide) {
                        r.add("INTERACCIÓN: " + a.nombre + " + " + b.nombre + ". " + in[2]);
                    }
                }
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ Seguridad

    public static String politicaPassword(String p) {
        if (p == null || p.length() < 8) {
            return "La contraseña debe tener mínimo 8 caracteres.";
        }
        if (!p.matches(".*[A-Z].*")) {
            return "La contraseña debe tener al menos una letra mayúscula.";
        }
        if (!p.matches(".*[0-9].*")) {
            return "La contraseña debe tener al menos un número.";
        }
        if (!p.matches(".*[^A-Za-z0-9].*")) {
            return "La contraseña debe tener al menos un símbolo (por ejemplo * # $ !).";
        }
        return null;
    }

    // ------------------------------------------------------------------ Laboratorio

    public static boolean fueraDeRango(Double v, Double min, Double max) {
        return v != null && ((min != null && v < min) || (max != null && v > max));
    }

    // ------------------------------------------------------------------ auxiliares

    private static boolean menor(Number v, double limite) {
        return v != null && v.doubleValue() < limite;
    }

    private static boolean mayor(Number v, double limite) {
        return v != null && v.doubleValue() > limite;
    }

    private static boolean mayorIgual(Number v, double limite) {
        return v != null && v.doubleValue() >= limite;
    }

    private static boolean fuera(Number v, double min, double max) {
        return v != null && (v.doubleValue() < min || v.doubleValue() > max);
    }
}
