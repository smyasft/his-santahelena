package co.santahelena.his.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Ingreso del paciente a un servicio.
 * URGENCIAS: EN_ESPERA_TRIAJE → TRIADO → EN_ATENCION → ATENDIDO.
 * HOSPITALIZACION: PENDIENTE_CAMA → EN_CAMA → EGRESADO.
 */
@Document(collection = "admisiones")
public class Admision {
    @Id
    public String id;
    @Indexed
    public String pacienteId;
    public String pacienteNombre;
    public String pacienteDocumento;
    public String pacienteSexo;
    public String tipo;
    @Indexed
    public String estado;
    public LocalDateTime fechaIngreso = LocalDateTime.now();
    public LocalDateTime fechaEgreso;
    public String motivo;
    public String epsNombre;
    public String derechosEstado;
    public String creadoPor;

    // Urgencias
    public Integer triajeNivel;
    public LocalDateTime triajeFecha;

    // Hospitalización
    public String servicio;
    public boolean requiereAislamiento;
    public String ordenadoPor;
    public String camaId;
    public String camaCodigo;
    public LocalDateTime inicioEstancia;
    public boolean estanciaLiquidada;
    public boolean ordenEgreso;
    public LocalDateTime ordenEgresoFecha;
    public String ordenEgresoPor;
    public List<Traslado> traslados = new ArrayList<>();

    public static class Traslado {
        public LocalDateTime fecha;
        public String deCama;
        public String aCama;
        public String motivo;
        public String usuario;
    }
}
