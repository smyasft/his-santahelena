package co.santahelena.his.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import co.santahelena.his.model.Lote;
import co.santahelena.his.model.Medicamento;
import co.santahelena.his.model.Movimiento;
import co.santahelena.his.model.Prescripcion;
import co.santahelena.his.repo.LoteRepo;
import co.santahelena.his.repo.MedicamentoRepo;
import co.santahelena.his.repo.MovimientoRepo;
import co.santahelena.his.repo.PrescripcionRepo;
import co.santahelena.his.util.ReglaException;
import co.santahelena.his.util.Reglas;

@Service
public class FarmaciaService {
    private final PrescripcionRepo prescripciones;
    private final MedicamentoRepo medicamentos;
    private final LoteRepo lotes;
    private final MovimientoRepo movimientos;
    private final FacturacionService facturacion;
    private final AlertaService alertas;
    private final AuditoriaService auditoria;
    private final SesionService sesion;

    public FarmaciaService(PrescripcionRepo prescripciones, MedicamentoRepo medicamentos, LoteRepo lotes, MovimientoRepo movimientos,
                           FacturacionService facturacion, AlertaService alertas, AuditoriaService auditoria, SesionService sesion) {
        this.prescripciones = prescripciones;
        this.medicamentos = medicamentos;
        this.lotes = lotes;
        this.movimientos = movimientos;
        this.facturacion = facturacion;
        this.alertas = alertas;
        this.auditoria = auditoria;
        this.sesion = sesion;
    }

    public Prescripcion prescripcion(String id) {
        return prescripciones.findById(id).orElseThrow(() -> new ReglaException("La fórmula no existe."));
    }

    public int stock(String medicamentoId) {
        return Reglas.stockVigente(lotes.findByMedicamentoId(medicamentoId), LocalDate.now());
    }

    public void validar(String id, boolean aprobada, String observacion) {
        Prescripcion p = prescripcion(id);
        if (!aprobada && (observacion == null || observacion.isBlank())) {
            throw new ReglaException("Escriba la observación para el médico.");
        }
        p.validacionFarmacia = aprobada ? "VALIDADA" : "OBSERVADA";
        p.observacionFarmacia = observacion;
        p.validadaPor = sesion.nombre();
        p.validadaEn = LocalDateTime.now();
        prescripciones.save(p);
        auditoria.registrar(aprobada ? "FORMULA_VALIDADA" : "FORMULA_OBSERVADA", "/farmacia", p.pacienteId, p.pacienteNombre, observacion);
    }

    /**
     * Dispensa por ítem con descuento FEFO, kárdex, cargo a la cuenta y alerta de stock mínimo.
     * RN-01: no se dispensa sin fórmula activa, vigente y validada por farmacia.
     */
    public void dispensar(String id, Map<Integer, Integer> cantidades) {
        Prescripcion p = prescripcion(id);
        if (!"ACTIVA".equals(p.estado)) {
            throw new ReglaException("La fórmula está " + p.estado + " y no se puede dispensar.");
        }
        if (p.vencida()) {
            p.estado = "VENCIDA";
            prescripciones.save(p);
            throw new ReglaException("La fórmula venció el " + p.venceEn + ". Solicite una nueva al médico.");
        }
        if (!"VALIDADA".equals(p.validacionFarmacia)) {
            throw new ReglaException("Primero valide la fórmula (estado actual: " + p.validacionFarmacia + ").");
        }
        LocalDate hoy = LocalDate.now();
        List<Object[]> plan = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : cantidades.entrySet()) {
            int q = e.getValue() == null ? 0 : e.getValue();
            if (q <= 0) {
                continue;
            }
            Prescripcion.Item it = p.items.get(e.getKey());
            if (q > it.pendiente()) {
                throw new ReglaException(it.nombre + ": la cantidad supera lo pendiente (" + it.pendiente() + ").");
            }
            List<Reglas.Asignacion> asig = Reglas.fefo(lotes.findByMedicamentoId(it.medicamentoId), q, hoy);
            if (asig == null) {
                throw new ReglaException("Stock vigente insuficiente de " + it.nombre + " (disponible " + stock(it.medicamentoId) + ").");
            }
            plan.add(new Object[] {e.getKey(), q, asig});
        }
        if (plan.isEmpty()) {
            throw new ReglaException("Indique al menos una cantidad a dispensar.");
        }
        for (Object[] x : plan) {
            int idx = (Integer) x[0];
            int q = (Integer) x[1];
            @SuppressWarnings("unchecked")
            List<Reglas.Asignacion> asig = (List<Reglas.Asignacion>) x[2];
            Prescripcion.Item it = p.items.get(idx);
            Medicamento m = medicamentos.findById(it.medicamentoId).orElseThrow();
            for (Reglas.Asignacion a : asig) {
                Lote l = a.lote;
                l.cantidad -= a.cantidad;
                lotes.save(l);
                Movimiento mv = new Movimiento();
                mv.medicamentoId = m.id;
                mv.medicamentoNombre = m.nombre;
                mv.controlEspecial = m.controlEspecial;
                mv.loteNumero = l.numero;
                mv.tipo = "DISPENSACION";
                mv.cantidad = -a.cantidad;
                mv.saldo = stock(m.id);
                mv.usuario = sesion.nombre();
                mv.pacienteId = p.pacienteId;
                mv.pacienteNombre = p.pacienteNombre;
                mv.pacienteDocumento = p.pacienteDocumento;
                mv.prescripcionId = p.id;
                mv.medicoNombre = p.medicoNombre;
                mv.referencia = "Fórmula " + p.id.substring(Math.max(0, p.id.length() - 6));
                movimientos.save(mv);
            }
            it.cantidadDispensada += q;
            facturacion.cargar(p.pacienteId, m.codigoCum, m.nombre, q, m.precio, "FARMACIA", "DISP-" + p.id + "-" + idx + "-" + it.cantidadDispensada);
            int saldo = stock(m.id);
            if (saldo < m.stockMinimo) {
                alertas.crear("FARMACIA", "STOCK_MINIMO", "MEDIA", null, null,
                    m.nombre + " quedó en " + saldo + " unidades (mínimo " + m.stockMinimo + ").", "/farmacia/inventario");
            }
        }
        boolean completa = p.items.stream().allMatch(i -> i.pendiente() == 0);
        if (completa) {
            p.estado = "DISPENSADA";
        }
        prescripciones.save(p);
        auditoria.registrar("DISPENSACION", "/farmacia", p.pacienteId, p.pacienteNombre, completa ? "Completa" : "Parcial");
    }

    public void ingresarLote(String medicamentoId, String numero, LocalDate vence, int cantidad, String proveedor) {
        Medicamento m = medicamentos.findById(medicamentoId).orElseThrow(() -> new ReglaException("Medicamento no encontrado."));
        if (numero == null || vence == null || cantidad <= 0) {
            throw new ReglaException("Lote, fecha de vencimiento y cantidad son obligatorios.");
        }
        if (vence.isBefore(LocalDate.now())) {
            throw new ReglaException("No se puede ingresar un lote vencido.");
        }
        for (Lote l : lotes.findByMedicamentoId(m.id)) {
            if (l.numero.equalsIgnoreCase(numero)) {
                throw new ReglaException("El lote " + numero + " ya existe para este medicamento.");
            }
        }
        Lote l = new Lote();
        l.medicamentoId = m.id;
        l.medicamentoNombre = m.nombre;
        l.numero = numero.toUpperCase();
        l.vence = vence;
        l.cantidad = cantidad;
        l.proveedor = proveedor;
        lotes.save(l);
        Movimiento mv = new Movimiento();
        mv.medicamentoId = m.id;
        mv.medicamentoNombre = m.nombre;
        mv.controlEspecial = m.controlEspecial;
        mv.loteNumero = l.numero;
        mv.tipo = "ENTRADA";
        mv.cantidad = cantidad;
        mv.saldo = stock(m.id);
        mv.usuario = sesion.nombre();
        mv.referencia = proveedor == null ? "Ingreso" : "Proveedor " + proveedor;
        movimientos.save(mv);
        auditoria.registrar("INGRESO_LOTE", "/farmacia/inventario", null, null, m.nombre + " lote " + l.numero + " x" + cantidad);
    }
}
