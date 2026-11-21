package co.santahelena.his.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "medicamentos")
public class Medicamento {
    @Id
    public String id;
    @Indexed(unique = true)
    public String codigoCum;
    public String nombre;
    public String formaFarmaceutica;
    public String viaPredeterminada;
    /** Grupo farmacológico para alertas de alergias e interacciones (AINE, IECA, OPIOIDE…). */
    public String grupo;
    public boolean controlEspecial;
    public int stockMinimo;
    public double precio;
    public boolean activo = true;

    // Datos oficiales cuando el medicamento se importa del listado CUM del INVIMA (datos.gov.co)
    public String principioActivo;
    public String titular;
    public String registroSanitario;
    public String atc;
    public String descripcionAtc;
    public String presentacion;
    public String fuente;
    public java.time.LocalDateTime importadoEn;

    public boolean deInvima() {
        return fuente != null;
    }
}
