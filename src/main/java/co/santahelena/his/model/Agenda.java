package co.santahelena.his.model;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Horario de atención de un profesional (base para las franjas de citas). */
@Document(collection = "agendas")
public class Agenda {
    @Id
    public String id;
    public String profesionalId;
    public String profesionalNombre;
    public String especialidad;
    public String sede;
    public String consultorio;
    /** 1 = lunes … 7 = domingo. */
    public Set<Integer> dias = new LinkedHashSet<>();
    public String mananaInicio = "07:00";
    public String mananaFin = "12:00";
    public String tardeInicio = "14:00";
    public String tardeFin = "17:00";
    public int duracionMinutos = 20;

    public String diasTexto() {
        String[] n = {"", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
        StringBuilder sb = new StringBuilder();
        for (Integer d : dias) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(n[d]);
        }
        return sb.toString();
    }
}
