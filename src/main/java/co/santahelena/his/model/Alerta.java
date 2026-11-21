package co.santahelena.his.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Alertas clínicas y operativas dirigidas a un rol (triaje I, valores críticos, stock mínimo…). */
@Document(collection = "alertas")
public class Alerta {
    @Id
    public String id;
    public LocalDateTime fecha = LocalDateTime.now();
    public String rolDestino;
    public String tipo;
    public String severidad;
    public String pacienteId;
    public String pacienteNombre;
    public String mensaje;
    public String enlace;
    public boolean leida;
    public String leidaPor;
    public LocalDateTime leidaEn;
}
