package co.santahelena.his.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import co.santahelena.his.model.ExamenLab;
import co.santahelena.his.model.OrdenLab;
import co.santahelena.his.model.Paciente;
import co.santahelena.his.repo.ExamenLabRepo;
import co.santahelena.his.repo.OrdenLabRepo;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.ReglaException;
import co.santahelena.his.util.Reglas;

@Service
public class LabService {
    private final OrdenLabRepo ordenes;
    private final ExamenLabRepo examenes;
    private final ConsecutivoService consecutivos;
    private final FacturacionService facturacion;
    private final AlertaService alertas;
    private final AuditoriaService auditoria;
    private final SesionService sesion;

    public LabService(OrdenLabRepo ordenes, ExamenLabRepo examenes, ConsecutivoService consecutivos, FacturacionService facturacion,
                      AlertaService alertas, AuditoriaService auditoria, SesionService sesion) {
        this.ordenes = ordenes;
        this.examenes = examenes;
        this.consecutivos = consecutivos;
        this.facturacion = facturacion;
        this.alertas = alertas;
        this.auditoria = auditoria;
        this.sesion = sesion;
    }

    public OrdenLab orden(String id) {
        return ordenes.findById(id).orElseThrow(() -> new ReglaException("La orden no existe."));
    }

    public OrdenLab crear(Paciente p, String atencionId, List<String> codigos, String prioridad, String observaciones, String medicoId, String medicoNombre) {
        if (codigos == null || codigos.isEmpty()) {
            throw new ReglaException("Seleccione al menos un examen.");
        }
        OrdenLab o = new OrdenLab();
        o.pacienteId = p.id;
        o.pacienteNombre = p.nombreCompleto();
        o.pacienteDocumento = p.documentoTexto();
        o.atencionId = atencionId;
        o.medicoId = medicoId;
        o.medicoNombre = medicoNombre;
        o.prioridad = prioridad == null ? "RUTINA" : prioridad;
        o.observaciones = observaciones;
        for (String c : codigos) {
            ExamenLab e = examenes.findByCodigo(c).orElseThrow(() -> new ReglaException("Examen " + c + " no existe."));
            OrdenLab.ExamenOrden eo = new OrdenLab.ExamenOrden();
            eo.codigo = e.codigo;
            eo.nombre = e.nombre;
            o.examenes.add(eo);
        }
        o = ordenes.save(o);
        auditoria.registrar("ORDEN_LAB", "/hce", p, o.examenesTexto());
        return o;
    }

    public OrdenLab recibir(String id) {
        OrdenLab o = orden(id);
        if (!"ORDENADA".equals(o.estado)) {
            throw new ReglaException("La orden ya fue " + o.estado.toLowerCase() + ".");
        }
        o.estado = "RECIBIDA";
        o.codigoMuestra = consecutivos.formato("muestra", "LAB", 7);
        o.recibidaEn = LocalDateTime.now();
        o.recibidaPor = sesion.nombre();
        ordenes.save(o);
        for (OrdenLab.ExamenOrden eo : o.examenes) {
            ExamenLab e = examenes.findByCodigo(eo.codigo).orElse(null);
            facturacion.cargar(o.pacienteId, eo.codigo, eo.nombre, 1, e == null ? 0 : e.precio, "LABORATORIO", "LAB-" + o.id + "-" + eo.codigo);
        }
        auditoria.registrar("MUESTRA_RECIBIDA", "/laboratorio", o.pacienteId, o.pacienteNombre, o.codigoMuestra);
        return o;
    }

    public void rechazar(String id, String motivo) {
        OrdenLab o = orden(id);
        if (motivo == null) {
            throw new ReglaException("Indique el motivo del rechazo de la muestra.");
        }
        if (!"ORDENADA".equals(o.estado) && !"RECIBIDA".equals(o.estado)) {
            throw new ReglaException("No se puede rechazar una orden con resultados.");
        }
        o.estado = "RECHAZADA";
        o.motivoRechazo = motivo;
        ordenes.save(o);
        alertas.crear("MEDICO", "MUESTRA_RECHAZADA", "MEDIA", o.pacienteId, o.pacienteNombre,
            "Muestra rechazada (" + o.examenesTexto() + "): " + motivo + ". Genere una nueva orden.", "/hce/historia/" + o.pacienteId);
        auditoria.registrar("MUESTRA_RECHAZADA", "/laboratorio", o.pacienteId, o.pacienteNombre, motivo);
    }

    /** Guarda resultados y marca fuera de rango y críticos según el catálogo (US-033). */
    public void registrarResultados(String id, Map<String, String> form) {
        OrdenLab o = orden(id);
        if (!"RECIBIDA".equals(o.estado) && !"RESULTADOS".equals(o.estado)) {
            throw new ReglaException("Solo se registran resultados de muestras recibidas.");
        }
        for (int i = 0; i < o.examenes.size(); i++) {
            OrdenLab.ExamenOrden eo = o.examenes.get(i);
            ExamenLab e = examenes.findByCodigo(eo.codigo).orElseThrow();
            eo.resultados.clear();
            for (int j = 0; j < e.analitos.size(); j++) {
                ExamenLab.Analito an = e.analitos.get(j);
                Double v = Form.d(form.get("v_" + i + "_" + j));
                if (v == null) {
                    throw new ReglaException("Falta el valor de " + an.nombre + " (" + eo.nombre + ").");
                }
                OrdenLab.Resultado r = new OrdenLab.Resultado();
                r.analito = an.nombre;
                r.valor = v;
                r.unidad = an.unidad;
                r.rango = an.rango();
                r.fueraRango = Reglas.fueraDeRango(v, an.min, an.max);
                r.critico = Reglas.fueraDeRango(v, an.criticoMin, an.criticoMax);
                eo.resultados.add(r);
            }
        }
        o.estado = "RESULTADOS";
        o.resultadosEn = LocalDateTime.now();
        o.resultadosPor = sesion.nombre();
        ordenes.save(o);
        auditoria.registrar("RESULTADOS_LAB", "/laboratorio", o.pacienteId, o.pacienteNombre, o.codigoMuestra);
    }

    public void validar(String id) {
        OrdenLab o = orden(id);
        if (!"RESULTADOS".equals(o.estado)) {
            throw new ReglaException("La orden no tiene resultados pendientes de validar.");
        }
        o.estado = "VALIDADA";
        o.validadaPor = sesion.nombre();
        o.validadaEn = LocalDateTime.now();
        ordenes.save(o);
        if (o.tieneCriticos()) {
            alertas.crear("MEDICO", "VALOR_CRITICO", "ALTA", o.pacienteId, o.pacienteNombre,
                "VALOR CRÍTICO en " + o.examenesTexto() + " (muestra " + o.codigoMuestra + "). Revise de inmediato.",
                "/hce/historia/" + o.pacienteId);
            alertas.crear("ENFERMERIA", "VALOR_CRITICO", "ALTA", o.pacienteId, o.pacienteNombre,
                "Valor crítico de laboratorio. Avise al médico tratante.", "/enfermeria/paciente/" + o.pacienteId);
        }
        auditoria.registrar("RESULTADOS_VALIDADOS", "/laboratorio", o.pacienteId, o.pacienteNombre, o.tieneCriticos() ? "Con valores críticos" : "Normal");
    }
}
