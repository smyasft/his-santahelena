package co.santahelena.his.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Orden de laboratorio. Estados: ORDENADA, RECIBIDA, RECHAZADA, RESULTADOS, VALIDADA. */
@Document(collection = "ordenes_lab")
public class OrdenLab {
    @Id
    public String id;
    @Indexed
    public String pacienteId;
    public String pacienteNombre;
    public String pacienteDocumento;
    public String atencionId;
    public String medicoId;
    public String medicoNombre;
    public LocalDateTime fecha = LocalDateTime.now();
    public String prioridad = "RUTINA";
    @Indexed
    public String estado = "ORDENADA";
    public String observaciones;
    public List<ExamenOrden> examenes = new ArrayList<>();
    public String codigoMuestra;
    public LocalDateTime recibidaEn;
    public String recibidaPor;
    public String motivoRechazo;
    public LocalDateTime resultadosEn;
    public String resultadosPor;
    public String validadaPor;
    public LocalDateTime validadaEn;

    public static class ExamenOrden {
        public String codigo;
        public String nombre;
        public List<Resultado> resultados = new ArrayList<>();
    }

    public static class Resultado {
        public String analito;
        public Double valor;
        public String unidad;
        public String rango;
        public boolean fueraRango;
        public boolean critico;
    }

    public boolean tieneCriticos() {
        for (ExamenOrden e : examenes) {
            for (Resultado r : e.resultados) {
                if (r.critico) {
                    return true;
                }
            }
        }
        return false;
    }

    public String examenesTexto() {
        return String.join(", ", examenes.stream().map(e -> e.nombre).toList());
    }
}
