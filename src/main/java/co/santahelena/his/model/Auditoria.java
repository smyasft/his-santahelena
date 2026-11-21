package co.santahelena.his.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Bitácora de solo inserción (RNF-06): la aplicación nunca actualiza ni borra estos registros. */
@Document(collection = "auditoria")
public class Auditoria {
    @Id
    public String id;
    @Indexed
    public LocalDateTime fecha;
    @Indexed
    public String usuario;
    public String roles;
    @Indexed
    public String pacienteId;
    public String pacienteNombre;
    public String recurso;
    @Indexed
    public String accion;
    public String ip;
    public String detalle;
}
