package co.santahelena.his.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "citas")
public class Cita {
    @Id
    public String id;
    @Indexed
    public String pacienteId;
    public String pacienteNombre;
    public String pacienteDocumento;
    @Indexed
    public String profesionalId;
    public String profesionalNombre;
    public String especialidad;
    public String sede;
    public String consultorio;
    @Indexed
    public LocalDateTime fechaHora;
    /** ASIGNADA, ATENDIDA, CANCELADA. */
    public String estado = "ASIGNADA";
    /** VENTANILLA o PORTAL. */
    public String canal;
    public String tipo;
    public LocalDateTime creadaEn = LocalDateTime.now();
    public String creadaPor;
    public LocalDateTime canceladaEn;
    public String motivoCancelacion;
}
