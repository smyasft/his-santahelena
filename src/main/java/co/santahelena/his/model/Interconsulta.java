package co.santahelena.his.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Remisión a especialista. Estados: SOLICITADA, ACEPTADA, RESPONDIDA. */
@Document(collection = "interconsultas")
public class Interconsulta {
    @Id
    public String id;
    public String pacienteId;
    public String pacienteNombre;
    public String atencionId;
    public String solicitanteNombre;
    public String especialidad;
    public String motivo;
    public String prioridad = "NORMAL";
    public String estado = "SOLICITADA";
    public String especialistaId;
    public String especialistaNombre;
    public LocalDateTime fecha = LocalDateTime.now();
    public LocalDateTime aceptadaEn;
    public String respuestaAtencionId;
    public LocalDateTime respondidaEn;
}
