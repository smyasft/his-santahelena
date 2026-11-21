package co.santahelena.his.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "consentimientos")
public class Consentimiento {
    @Id
    public String id;
    public String pacienteId;
    public String pacienteNombre;
    public String pacienteDocumento;
    public String procedimiento;
    public String descripcion;
    public String riesgos;
    public String alternativas;
    public boolean acepta;
    public String firmanteNombre;
    public String firmanteDocumento;
    public String parentesco;
    public String testigo;
    public String especialistaNombre;
    public String especialistaRegistro;
    public LocalDateTime fecha = LocalDateTime.now();
    public String hash;
}
