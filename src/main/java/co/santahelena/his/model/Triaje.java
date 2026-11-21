package co.santahelena.his.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "triajes")
public class Triaje {
    @Id
    public String id;
    @Indexed
    public String admisionId;
    @Indexed
    public String pacienteId;
    public String pacienteNombre;
    public int nivel;
    public int nivelSugerido;
    public String motivo;
    public String signosId;
    public String resumenSignos;
    public String enfermero;
    public LocalDateTime fecha = LocalDateTime.now();
}
