package co.santahelena.his.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Cama hospitalaria. Estados: LIBRE, OCUPADA, LIMPIEZA, BLOQUEADA. */
@Document(collection = "camas")
public class Cama {
    @Id
    public String id;
    @Indexed(unique = true)
    public String codigo;
    public String servicio;
    public String piso;
    public String habitacion;
    public String estado = "LIBRE";
    /** M, F o null (mixta). */
    public String sexoPermitido;
    public boolean aislamiento;
    public String pacienteId;
    public String pacienteNombre;
    public String admisionId;
    public LocalDateTime desde;
    public String motivoBloqueo;

    public String restriccionTexto() {
        String s = sexoPermitido == null ? "Mixta" : ("M".equals(sexoPermitido) ? "Hombres" : "Mujeres");
        return aislamiento ? s + " · Aislamiento" : s;
    }
}
