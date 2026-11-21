package co.santahelena.his.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Factura electrónica de venta. El envío a la DIAN es simulado en esta versión. */
@Document(collection = "facturas")
public class Factura {
    @Id
    public String id;
    @Indexed(unique = true)
    public String numero;
    public String cuentaId;
    @Indexed
    public String pacienteId;
    public String pacienteNombre;
    public String pacienteDocumento;
    public String pacienteTipoDocumento;
    public String pacienteSexo;
    public String pacienteFechaNacimiento;
    public String epsId;
    public String epsNombre;
    public String epsCodigo;
    public String adquirenteNombre;
    public String adquirenteDocumento;
    public boolean particular;
    @Indexed
    public LocalDateTime fecha = LocalDateTime.now();
    public List<Cuenta.Cargo> items = new ArrayList<>();
    public double subtotal;
    public double total;
    public String cufe;
    public String estadoDian;
    public boolean pagada;
    public String formaPago;
    public LocalDateTime pagadaEn;
    public String usuario;
}
