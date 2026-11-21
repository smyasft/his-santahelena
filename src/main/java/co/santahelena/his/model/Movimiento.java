package co.santahelena.his.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Kárdex de inventario: cada entrada y salida queda registrada con el saldo resultante. */
@Document(collection = "movimientos")
public class Movimiento {
    @Id
    public String id;
    public LocalDateTime fecha = LocalDateTime.now();
    @Indexed
    public String medicamentoId;
    public String medicamentoNombre;
    public boolean controlEspecial;
    public String loteNumero;
    /** ENTRADA o DISPENSACION. */
    public String tipo;
    public int cantidad;
    public int saldo;
    public String usuario;
    public String pacienteId;
    public String pacienteNombre;
    public String pacienteDocumento;
    public String prescripcionId;
    public String medicoNombre;
    public String referencia;
}
