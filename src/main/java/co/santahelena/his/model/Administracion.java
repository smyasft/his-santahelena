package co.santahelena.his.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Registro de administración de medicamentos por enfermería (US-020). */
@Document(collection = "administraciones")
public class Administracion {
    @Id
    public String id;
    @Indexed
    public String pacienteId;
    public String prescripcionId;
    public int itemIndice;
    public String medicamento;
    public String dosisOrdenada;
    public String viaOrdenada;
    public String dosis;
    public String via;
    public String observacion;
    public LocalDateTime fecha = LocalDateTime.now();
    public String enfermero;
    public String alerta;
    public boolean anulada;
    public String justificacionAnulacion;
    public String anuladaPor;
    public LocalDateTime anuladaEn;
}
