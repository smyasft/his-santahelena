package co.santahelena.his.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Cuenta del paciente: acumula los cargos automáticos hasta que se factura (US-041). */
@Document(collection = "cuentas")
public class Cuenta {
    @Id
    public String id;
    @Indexed
    public String pacienteId;
    public String pacienteNombre;
    public String pacienteDocumento;
    public String epsId;
    public String epsNombre;
    /** ABIERTA o FACTURADA. */
    @Indexed
    public String estado = "ABIERTA";
    public LocalDateTime abiertaEn = LocalDateTime.now();
    public List<Cargo> cargos = new ArrayList<>();
    public String facturaId;
    public String facturaNumero;

    public static class Cargo {
        public LocalDateTime fecha = LocalDateTime.now();
        public String codigo;
        public String descripcion;
        public int cantidad;
        public double valorUnitario;
        public double valorTotal;
        public String origen;
        public String referencia;
    }

    public double total() {
        double t = 0;
        for (Cargo c : cargos) {
            t += c.valorTotal;
        }
        return t;
    }
}
