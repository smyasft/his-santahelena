package co.santahelena.his.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import co.santahelena.his.model.Admision;
import co.santahelena.his.model.Configuracion;
import co.santahelena.his.model.Cuenta;
import co.santahelena.his.model.Eps;
import co.santahelena.his.model.Factura;
import co.santahelena.his.model.Paciente;
import co.santahelena.his.model.PazYSalvo;
import co.santahelena.his.repo.AdmisionRepo;
import co.santahelena.his.repo.CatalogoRepo;
import co.santahelena.his.repo.CuentaRepo;
import co.santahelena.his.repo.EpsRepo;
import co.santahelena.his.repo.FacturaRepo;
import co.santahelena.his.repo.PacienteRepo;
import co.santahelena.his.repo.PazYSalvoRepo;
import co.santahelena.his.util.Hash;
import co.santahelena.his.util.ReglaException;

@Service
public class FacturacionService {
    private final CuentaRepo cuentas;
    private final FacturaRepo facturas;
    private final EpsRepo eps;
    private final PacienteRepo pacientes;
    private final CatalogoRepo catalogos;
    private final AdmisionRepo admisiones;
    private final PazYSalvoRepo pazYSalvos;
    private final ConsecutivoService consecutivos;
    private final AuditoriaService auditoria;
    private final SesionService sesion;
    private final ConfigService config;

    public FacturacionService(CuentaRepo cuentas, FacturaRepo facturas, EpsRepo eps, PacienteRepo pacientes, CatalogoRepo catalogos,
                              AdmisionRepo admisiones, PazYSalvoRepo pazYSalvos, ConsecutivoService consecutivos,
                              AuditoriaService auditoria, SesionService sesion, ConfigService config) {
        this.cuentas = cuentas;
        this.facturas = facturas;
        this.eps = eps;
        this.pacientes = pacientes;
        this.catalogos = catalogos;
        this.admisiones = admisiones;
        this.pazYSalvos = pazYSalvos;
        this.consecutivos = consecutivos;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.config = config;
    }

    public double tarifaCups(String codigo, double porDefecto) {
        return catalogos.findByTipoAndCodigo("CUPS", codigo).map(c -> c.valor == null ? porDefecto : c.valor).orElse(porDefecto);
    }

    public Cuenta cuentaAbierta(Paciente p) {
        return cuentas.findFirstByPacienteIdAndEstado(p.id, "ABIERTA").orElseGet(() -> {
            Cuenta c = new Cuenta();
            c.pacienteId = p.id;
            c.pacienteNombre = p.nombreCompleto();
            c.pacienteDocumento = p.documentoTexto();
            c.epsId = p.epsId;
            c.epsNombre = p.epsId == null ? "Particular" : p.epsNombre;
            return cuentas.save(c);
        });
    }

    private double factor(String epsId) {
        if (epsId == null) {
            return 1.0;
        }
        return eps.findById(epsId).map(e -> e.factorTarifa).orElse(1.0);
    }

    /**
     * Carga automática a la cuenta (US-041). La referencia evita cargar dos veces el mismo servicio.
     * El valor unitario aplica el factor del convenio de la EPS (RN-12).
     */
    public void cargar(String pacienteId, String codigo, String descripcion, int cantidad, double valorBase, String origen, String referencia) {
        Paciente p = pacientes.findById(pacienteId).orElse(null);
        if (p == null || cantidad <= 0) {
            return;
        }
        Cuenta c = cuentaAbierta(p);
        if (referencia != null) {
            for (Cuenta.Cargo x : c.cargos) {
                if (referencia.equals(x.referencia)) {
                    return;
                }
            }
        }
        Cuenta.Cargo cg = new Cuenta.Cargo();
        cg.codigo = codigo;
        cg.descripcion = descripcion;
        cg.cantidad = cantidad;
        cg.valorUnitario = Math.round(valorBase * factor(c.epsId));
        cg.valorTotal = cg.valorUnitario * cantidad;
        cg.origen = origen;
        cg.referencia = referencia;
        c.cargos.add(cg);
        cuentas.save(c);
    }

    public Factura facturar(String cuentaId) {
        Cuenta c = cuentas.findById(cuentaId).orElseThrow(() -> new ReglaException("La cuenta no existe."));
        if (!"ABIERTA".equals(c.estado)) {
            throw new ReglaException("La cuenta ya fue facturada.");
        }
        if (c.cargos.isEmpty()) {
            throw new ReglaException("La cuenta no tiene cargos para facturar.");
        }
        Paciente p = pacientes.findById(c.pacienteId).orElseThrow();
        Configuracion cfg = config.get();
        Factura f = new Factura();
        f.numero = "FEHS" + consecutivos.siguiente("factura");
        f.cuentaId = c.id;
        f.pacienteId = p.id;
        f.pacienteNombre = p.nombreCompleto();
        f.pacienteDocumento = p.numeroDocumento;
        f.pacienteTipoDocumento = p.tipoDocumento;
        f.pacienteSexo = p.sexo;
        f.pacienteFechaNacimiento = p.fechaNacimiento == null ? null : p.fechaNacimiento.toString();
        f.particular = c.epsId == null;
        if (!f.particular) {
            Eps e = eps.findById(c.epsId).orElse(null);
            f.epsId = c.epsId;
            f.epsNombre = e == null ? c.epsNombre : e.nombre;
            f.epsCodigo = e == null ? null : e.codigo;
            f.adquirenteNombre = f.epsNombre;
            f.adquirenteDocumento = e == null ? null : "NIT " + e.nit;
        } else {
            f.epsNombre = "Particular";
            f.adquirenteNombre = p.nombreCompleto();
            f.adquirenteDocumento = p.documentoTexto();
        }
        f.items = new ArrayList<>(c.cargos);
        f.subtotal = c.total();
        f.total = f.subtotal;
        f.usuario = sesion.nombre();
        f.fecha = LocalDateTime.now();
        // CUFE SIMULADO: en producción lo calcula el proveedor tecnológico habilitado por la DIAN.
        f.cufe = Hash.sha384(f.numero + f.fecha + String.format("%.2f", f.total) + cfg.nit + f.adquirenteDocumento + "SIMULACION");
        f.estadoDian = "ACEPTADA (SIMULACIÓN)";
        f = facturas.save(f);
        c.estado = "FACTURADA";
        c.facturaId = f.id;
        c.facturaNumero = f.numero;
        cuentas.save(c);
        auditoria.registrar("FACTURA_EMITIDA", "/facturacion", p, f.numero + " por $" + String.format("%,.0f", f.total));
        return f;
    }

    public void registrarPago(String facturaId, String formaPago) {
        Factura f = facturas.findById(facturaId).orElseThrow(() -> new ReglaException("La factura no existe."));
        if (f.pagada) {
            throw new ReglaException("La factura ya tiene pago registrado.");
        }
        f.pagada = true;
        f.formaPago = formaPago == null ? "EFECTIVO" : formaPago;
        f.pagadaEn = LocalDateTime.now();
        facturas.save(f);
        auditoria.registrar("PAGO_REGISTRADO", "/facturacion", f.pacienteId, f.pacienteNombre, f.numero + " – " + f.formaPago);
    }

    /** Devuelve null si el paciente puede recibir paz y salvo; si no, el motivo. */
    public String motivoSinPazYSalvo(Admision a) {
        if ("HOSPITALIZACION".equals(a.tipo) && !a.estanciaLiquidada) {
            return "La estancia hospitalaria aún no ha sido liquidada por el gestor de camas.";
        }
        Cuenta c = cuentas.findFirstByPacienteIdAndEstado(a.pacienteId, "ABIERTA").orElse(null);
        if (c != null && !c.cargos.isEmpty()) {
            return "El paciente tiene una cuenta abierta con cargos sin facturar ($" + String.format("%,.0f", c.total()) + ").";
        }
        for (Factura f : facturas.findByPacienteIdOrderByFechaDesc(a.pacienteId)) {
            if (f.particular && !f.pagada) {
                return "La factura " + f.numero + " (particular) está pendiente de pago.";
            }
        }
        return null;
    }

    public PazYSalvo expedirPazYSalvo(String admisionId) {
        Admision a = admisiones.findById(admisionId).orElseThrow(() -> new ReglaException("Admisión no encontrada."));
        PazYSalvo previo = pazYSalvos.findFirstByAdmisionId(admisionId).orElse(null);
        if (previo != null) {
            return previo;
        }
        String motivo = motivoSinPazYSalvo(a);
        if (motivo != null) {
            throw new ReglaException("No se puede expedir el paz y salvo: " + motivo);
        }
        PazYSalvo p = new PazYSalvo();
        p.numero = consecutivos.formato("pazysalvo", "PYS-", 5);
        p.pacienteId = a.pacienteId;
        p.pacienteNombre = a.pacienteNombre;
        p.pacienteDocumento = a.pacienteDocumento;
        p.admisionId = a.id;
        p.expedidoPor = sesion.nombre();
        List<String> nums = new ArrayList<>();
        for (Factura f : facturas.findByPacienteIdOrderByFechaDesc(a.pacienteId)) {
            nums.add(f.numero);
        }
        p.facturas = String.join(", ", nums);
        p = pazYSalvos.save(p);
        auditoria.registrar("PAZ_Y_SALVO", "/facturacion/paz-y-salvo", a.pacienteId, a.pacienteNombre, p.numero);
        return p;
    }

    /** RIPS en formato JSON (estructura simplificada de la Res. 2275 de 2023). */
    public Map<String, Object> rips(LocalDate desde, LocalDate hasta) {
        Configuracion cfg = config.get();
        List<Map<String, Object>> usuarios = new ArrayList<>();
        for (Factura f : facturas.findByFechaBetweenOrderByFechaAsc(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay())) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("numFactura", f.numero);
            u.put("tipoDocumentoIdentificacion", f.pacienteTipoDocumento);
            u.put("numDocumentoIdentificacion", f.pacienteDocumento);
            u.put("codSexo", f.pacienteSexo);
            u.put("fechaNacimiento", f.pacienteFechaNacimiento);
            u.put("codEntidadAdministradora", f.particular ? "PARTICULAR" : f.epsCodigo);
            List<Map<String, Object>> consultas = new ArrayList<>();
            List<Map<String, Object>> procedimientos = new ArrayList<>();
            List<Map<String, Object>> medicamentos = new ArrayList<>();
            List<Map<String, Object>> otros = new ArrayList<>();
            for (Cuenta.Cargo c : f.items) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("fechaInicioAtencion", c.fecha == null ? null : c.fecha.toString());
                s.put("codServicio", c.codigo);
                s.put("descripcion", c.descripcion);
                s.put("cantidad", c.cantidad);
                s.put("vrServicio", c.valorTotal);
                if ("CONSULTA".equals(c.origen)) {
                    consultas.add(s);
                } else if ("FARMACIA".equals(c.origen)) {
                    medicamentos.add(s);
                } else if ("LABORATORIO".equals(c.origen)) {
                    procedimientos.add(s);
                } else {
                    otros.add(s);
                }
            }
            Map<String, Object> serv = new LinkedHashMap<>();
            serv.put("consultas", consultas);
            serv.put("procedimientos", procedimientos);
            serv.put("medicamentos", medicamentos);
            serv.put("otrosServicios", otros);
            u.put("servicios", serv);
            usuarios.add(u);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("numDocumentoIdObligado", cfg.nit);
        r.put("codPrestador", cfg.codigoHabilitacion);
        r.put("periodo", desde + " a " + hasta);
        r.put("nota", "Archivo de prueba generado por el HIS académico; validar contra el MUV del Ministerio antes de reportar.");
        r.put("usuarios", usuarios);
        auditoria.registrar("RIPS_GENERADO", "/facturacion/rips", null, null, desde + " a " + hasta + " – " + usuarios.size() + " facturas");
        return r;
    }
}
