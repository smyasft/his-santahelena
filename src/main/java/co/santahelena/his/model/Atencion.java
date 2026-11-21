package co.santahelena.his.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Registro de historia clínica. Una vez firmado no se modifica (RN-06, Res. 1995 de 1999). */
@Document(collection = "atenciones")
public class Atencion {
    @Id
    public String id;
    @Indexed
    public String pacienteId;
    public String pacienteNombre;
    public String admisionId;
    public String citaId;
    public String interconsultaId;
    public String profesionalId;
    public String profesionalNombre;
    public String profesionalRegistro;
    public String especialidad;
    /** CONSULTA, URGENCIAS, EVOLUCION, ESPECIALIDAD, ACLARATORIA. */
    public String tipo = "CONSULTA";
    public String motivoConsulta;
    public String enfermedadActual;
    public String antecedentes;
    public String examenFisico;
    public String analisis;
    public String plan;
    public List<Campo> camposEspecialidad = new ArrayList<>();
    public List<Diagnostico> diagnosticos = new ArrayList<>();
    public boolean ordenHospitalizacion;
    public String servicioHospitalizacion;
    public boolean requiereAislamiento;
    public boolean ordenEgreso;
    public String aclaraA;
    public LocalDateTime fecha = LocalDateTime.now();
    public boolean firmada;
    public LocalDateTime fechaFirma;
    public String hashFirma;

    public static class Diagnostico {
        public String codigo;
        public String descripcion;
        public String tipo;
    }

    public static class Campo {
        public String etiqueta;
        public String valor;
    }

    public String diagnosticoPrincipal() {
        return diagnosticos.isEmpty() ? "—" : diagnosticos.get(0).codigo + " " + diagnosticos.get(0).descripcion;
    }
}
