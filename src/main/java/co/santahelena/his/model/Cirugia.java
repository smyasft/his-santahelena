package co.santahelena.his.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cirugias")
public class Cirugia {
    @Id
    public String id;
    public String pacienteId;
    public String pacienteNombre;
    public String pacienteDocumento;
    public String procedimientoCodigo;
    public String procedimientoNombre;
    public String quirofano;
    public LocalDateTime inicio;
    public LocalDateTime fin;
    public int duracionMinutos;
    public String anestesia;
    public String especialistaNombre;
    public String consentimientoId;
    /** PROGRAMADA, REALIZADA, CANCELADA. */
    public String estado = "PROGRAMADA";
    public String programadaPor;
}
