package co.santahelena.his.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "paz_y_salvos")
public class PazYSalvo {
    @Id
    public String id;
    public String numero;
    @Indexed
    public String pacienteId;
    public String pacienteNombre;
    public String pacienteDocumento;
    public String admisionId;
    public LocalDateTime fecha = LocalDateTime.now();
    public String expedidoPor;
    public String facturas;
}
