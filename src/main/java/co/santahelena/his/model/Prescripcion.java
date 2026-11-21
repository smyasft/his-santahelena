package co.santahelena.his.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Fórmula médica electrónica. Estados: ACTIVA, DISPENSADA, VENCIDA, ANULADA. */
@Document(collection = "prescripciones")
public class Prescripcion {
    @Id
    public String id;
    @Indexed
    public String pacienteId;
    public String pacienteNombre;
    public String pacienteDocumento;
    public String atencionId;
    public String medicoId;
    public String medicoNombre;
    public String medicoRegistro;
    public LocalDateTime fecha = LocalDateTime.now();
    public LocalDate venceEn;
    @Indexed
    public String estado = "ACTIVA";
    public List<Item> items = new ArrayList<>();
    public List<String> alertas = new ArrayList<>();
    public String justificacionAlertas;
    /** PENDIENTE, VALIDADA u OBSERVADA por el farmaceuta. */
    public String validacionFarmacia = "PENDIENTE";
    public String observacionFarmacia;
    public String validadaPor;
    public LocalDateTime validadaEn;
    public String hashFirma;

    public static class Item {
        public String medicamentoId;
        public String codigoCum;
        public String nombre;
        public String dosis;
        public String via;
        public String frecuencia;
        public int dias;
        public int cantidad;
        public int cantidadDispensada;
        public boolean controlEspecial;
        public String justificacionControl;

        public int pendiente() {
            return cantidad - cantidadDispensada;
        }
    }

    public boolean vencida() {
        return venceEn != null && venceEn.isBefore(LocalDate.now());
    }
}
