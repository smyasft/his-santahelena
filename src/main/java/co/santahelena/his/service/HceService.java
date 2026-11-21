package co.santahelena.his.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import co.santahelena.his.model.Admision;
import co.santahelena.his.model.Atencion;
import co.santahelena.his.model.Cita;
import co.santahelena.his.model.Interconsulta;
import co.santahelena.his.model.Medicamento;
import co.santahelena.his.model.Paciente;
import co.santahelena.his.model.Prescripcion;
import co.santahelena.his.model.Usuario;
import co.santahelena.his.repo.AdmisionRepo;
import co.santahelena.his.repo.AtencionRepo;
import co.santahelena.his.repo.CitaRepo;
import co.santahelena.his.repo.InterconsultaRepo;
import co.santahelena.his.repo.MedicamentoRepo;
import co.santahelena.his.repo.PrescripcionRepo;
import co.santahelena.his.util.Hash;
import co.santahelena.his.util.ReglaException;
import co.santahelena.his.util.Reglas;

@Service
public class HceService {
    private final AtencionRepo atenciones;
    private final AdmisionRepo admisiones;
    private final CitaRepo citas;
    private final InterconsultaRepo interconsultas;
    private final PrescripcionRepo prescripciones;
    private final MedicamentoRepo medicamentos;
    private final PacienteService pacientes;
    private final FacturacionService facturacion;
    private final AlertaService alertas;
    private final AuditoriaService auditoria;
    private final SesionService sesion;

    public HceService(AtencionRepo atenciones, AdmisionRepo admisiones, CitaRepo citas, InterconsultaRepo interconsultas,
                      PrescripcionRepo prescripciones, MedicamentoRepo medicamentos, PacienteService pacientes,
                      FacturacionService facturacion, AlertaService alertas, AuditoriaService auditoria, SesionService sesion) {
        this.atenciones = atenciones;
        this.admisiones = admisiones;
        this.citas = citas;
        this.interconsultas = interconsultas;
        this.prescripciones = prescripciones;
        this.medicamentos = medicamentos;
        this.pacientes = pacientes;
        this.facturacion = facturacion;
        this.alertas = alertas;
        this.auditoria = auditoria;
        this.sesion = sesion;
    }

    public Atencion atencion(String id) {
        return atenciones.findById(id).orElseThrow(() -> new ReglaException("El registro no existe."));
    }

    public Atencion nueva(Paciente p, String tipo, String citaId, String admisionId, String interconsultaId) {
        Usuario u = sesion.requerido();
        Atencion a = new Atencion();
        a.pacienteId = p.id;
        a.pacienteNombre = p.nombreCompleto();
        a.profesionalId = u.id;
        a.profesionalNombre = u.nombre;
        a.profesionalRegistro = u.registroMedico;
        a.especialidad = u.especialidad == null ? "Medicina General" : u.especialidad;
        a.tipo = tipo;
        a.citaId = citaId;
        a.admisionId = admisionId;
        a.interconsultaId = interconsultaId;
        Cita c = citaId == null ? null : citas.findById(citaId).orElse(null);
        if (c != null && c.tipo != null) {
            a.motivoConsulta = c.tipo;
        }
        return atenciones.save(a);
    }

    public void verificarEditable(Atencion a) {
        Usuario u = sesion.requerido();
        if (a.firmada) {
            throw new ReglaException("El registro está firmado y no se puede modificar. Use una nota aclaratoria.");
        }
        if (!u.id.equals(a.profesionalId)) {
            throw new ReglaException("Solo el profesional que abrió el registro puede editarlo.");
        }
    }

    public Atencion guardar(Atencion a) {
        verificarEditable(a);
        return atenciones.save(a);
    }

    /** Firma: sella con SHA-256 y dispara los efectos (cita atendida, hospitalización, egreso, cargo). */
    public void firmar(String id) {
        Atencion a = atencion(id);
        verificarEditable(a);
        if (a.diagnosticos.isEmpty()) {
            throw new ReglaException("Registre al menos un diagnóstico CIE-10 antes de firmar.");
        }
        if (a.motivoConsulta == null || a.analisis == null || a.plan == null) {
            throw new ReglaException("Motivo de consulta, análisis y plan son obligatorios para firmar.");
        }
        if (a.ordenHospitalizacion && a.servicioHospitalizacion == null) {
            throw new ReglaException("Indique el servicio de hospitalización.");
        }
        a.firmada = true;
        a.fechaFirma = LocalDateTime.now();
        a.hashFirma = Hash.sha256(a.pacienteId + "|" + a.profesionalId + "|" + a.fechaFirma + "|" + a.motivoConsulta + "|"
            + a.enfermedadActual + "|" + a.examenFisico + "|" + a.analisis + "|" + a.plan + "|" + a.diagnosticoPrincipal());
        atenciones.save(a);
        Paciente p = pacientes.obtener(a.pacienteId);

        if (a.citaId != null) {
            citas.findById(a.citaId).ifPresent(c -> {
                c.estado = "ATENDIDA";
                citas.save(c);
            });
        }
        Admision adm = a.admisionId == null ? null : admisiones.findById(a.admisionId).orElse(null);
        if (adm != null && "URGENCIAS".equals(adm.tipo) && !"ATENDIDO".equals(adm.estado)) {
            adm.estado = "ATENDIDO";
            adm.fechaEgreso = a.ordenHospitalizacion ? null : LocalDateTime.now();
            admisiones.save(adm);
        }
        if (a.interconsultaId != null) {
            interconsultas.findById(a.interconsultaId).ifPresent(ic -> {
                ic.estado = "RESPONDIDA";
                ic.respuestaAtencionId = a.id;
                ic.respondidaEn = LocalDateTime.now();
                interconsultas.save(ic);
            });
        }
        if (a.ordenHospitalizacion) {
            boolean yaHospitalizado = admisiones.findByPacienteIdOrderByFechaIngresoDesc(p.id).stream()
                .anyMatch(x -> "HOSPITALIZACION".equals(x.tipo) && !"EGRESADO".equals(x.estado));
            if (!yaHospitalizado) {
                Admision h = new Admision();
                h.pacienteId = p.id;
                h.pacienteNombre = p.nombreCompleto();
                h.pacienteDocumento = p.documentoTexto();
                h.pacienteSexo = p.sexo;
                h.tipo = "HOSPITALIZACION";
                h.estado = "PENDIENTE_CAMA";
                h.servicio = a.servicioHospitalizacion;
                h.requiereAislamiento = a.requiereAislamiento;
                h.motivo = a.diagnosticoPrincipal();
                h.ordenadoPor = a.profesionalNombre;
                h.epsNombre = p.epsNombre;
                h.creadoPor = a.profesionalNombre;
                admisiones.save(h);
                alertas.crear("CAMAS", "SOLICITUD_CAMA", "MEDIA", p.id, p.nombreCompleto(),
                    "Orden de hospitalización en " + h.servicio + (h.requiereAislamiento ? " con aislamiento" : "") + ".", "/camas/pendientes");
            }
        }
        if (a.ordenEgreso) {
            for (Admision x : admisiones.findByPacienteIdOrderByFechaIngresoDesc(p.id)) {
                if ("HOSPITALIZACION".equals(x.tipo) && "EN_CAMA".equals(x.estado)) {
                    x.ordenEgreso = true;
                    x.ordenEgresoFecha = LocalDateTime.now();
                    x.ordenEgresoPor = a.profesionalNombre;
                    admisiones.save(x);
                    alertas.crear("CAMAS", "ORDEN_EGRESO", "MEDIA", p.id, p.nombreCompleto(),
                        "Orden de egreso cama " + x.camaCodigo + ". Liquide la estancia.", "/camas");
                    alertas.crear("FACTURACION", "ORDEN_EGRESO", "MEDIA", p.id, p.nombreCompleto(),
                        "Paciente con orden de egreso: prepare factura y paz y salvo.", "/facturacion/paz-y-salvo");
                }
            }
        }
        if (!"ACLARATORIA".equals(a.tipo)) {
            String codigo;
            String desc;
            if ("ESPECIALIDAD".equals(a.tipo)) {
                codigo = "890202";
                desc = "Consulta de primera vez por medicina especializada";
            } else if ("URGENCIAS".equals(a.tipo)) {
                codigo = "890701";
                desc = "Consulta de urgencias por medicina general";
            } else if ("EVOLUCION".equals(a.tipo)) {
                codigo = "890602";
                desc = "Atención (visita) intrahospitalaria por medicina general";
            } else {
                codigo = "890201";
                desc = "Consulta de primera vez por medicina general";
            }
            facturacion.cargar(p.id, codigo, desc, 1, facturacion.tarifaCups(codigo, 45000), "CONSULTA", "ATN-" + a.id);
        }
        auditoria.registrar("HC_FIRMADA", "/hce/atencion/" + a.id, p, a.tipo + " " + a.diagnosticoPrincipal());
    }

    public Atencion aclaratoria(String atencionId, String texto) {
        Atencion original = atencion(atencionId);
        if (!original.firmada) {
            throw new ReglaException("El registro aún no está firmado; puede editarlo directamente.");
        }
        if (texto == null) {
            throw new ReglaException("Escriba el texto de la nota aclaratoria.");
        }
        Paciente p = pacientes.obtener(original.pacienteId);
        Atencion n = nueva(p, "ACLARATORIA", null, original.admisionId, null);
        n.aclaraA = original.id;
        n.motivoConsulta = "Nota aclaratoria del registro del " + original.fecha.toLocalDate();
        n.analisis = texto;
        n.plan = "Sin cambios adicionales";
        n.diagnosticos = new ArrayList<>(original.diagnosticos);
        atenciones.save(n);
        firmar(n.id);
        return n;
    }

    public List<String> alertasFormula(Paciente p, List<Prescripcion.Item> items) {
        List<Medicamento> nuevos = new ArrayList<>();
        for (Prescripcion.Item it : items) {
            medicamentos.findById(it.medicamentoId).ifPresent(nuevos::add);
        }
        List<Medicamento> activos = new ArrayList<>();
        for (Prescripcion pr : prescripciones.findByPacienteIdAndEstado(p.id, "ACTIVA")) {
            for (Prescripcion.Item it : pr.items) {
                medicamentos.findById(it.medicamentoId).ifPresent(activos::add);
            }
        }
        return Reglas.alertasPrescripcion(nuevos, activos, p.alergias);
    }

    /** Fórmula electrónica: con alertas exige justificación; los de control especial también (US-029/030). */
    public Prescripcion prescribir(Paciente p, String atencionId, List<Prescripcion.Item> items, String justificacion) {
        if (items.isEmpty()) {
            throw new ReglaException("Agregue al menos un medicamento.");
        }
        Usuario u = sesion.requerido();
        for (Prescripcion.Item it : items) {
            Medicamento m = medicamentos.findById(it.medicamentoId).orElseThrow(() -> new ReglaException("Medicamento no válido."));
            it.codigoCum = m.codigoCum;
            it.nombre = m.nombre;
            it.controlEspecial = m.controlEspecial;
            if (it.dosis == null || it.frecuencia == null || it.cantidad <= 0) {
                throw new ReglaException(m.nombre + ": dosis, frecuencia y cantidad son obligatorias.");
            }
            if (m.controlEspecial && it.justificacionControl == null) {
                throw new ReglaException(m.nombre + " es de control especial: escriba la justificación clínica.");
            }
        }
        List<String> al = alertasFormula(p, items);
        if (!al.isEmpty() && justificacion == null) {
            throw new ReglaException("La fórmula tiene alertas; escriba la justificación para continuar.");
        }
        Prescripcion pr = new Prescripcion();
        pr.pacienteId = p.id;
        pr.pacienteNombre = p.nombreCompleto();
        pr.pacienteDocumento = p.documentoTexto();
        pr.atencionId = atencionId;
        pr.medicoId = u.id;
        pr.medicoNombre = u.nombre;
        pr.medicoRegistro = u.registroMedico;
        pr.items = items;
        pr.alertas = al;
        pr.justificacionAlertas = justificacion;
        pr.venceEn = LocalDate.now().plusDays(30);
        pr.hashFirma = Hash.sha256(p.id + "|" + u.id + "|" + LocalDateTime.now() + "|" + items.size());
        pr = prescripciones.save(pr);
        auditoria.registrar("FORMULA_EMITIDA", "/hce", p, items.size() + " medicamento(s)" + (al.isEmpty() ? "" : " con " + al.size() + " alerta(s) justificadas"));
        return pr;
    }

    public Interconsulta solicitarInterconsulta(Paciente p, String atencionId, String especialidad, String motivo, String prioridad) {
        if (especialidad == null || motivo == null) {
            throw new ReglaException("Especialidad y motivo son obligatorios.");
        }
        Interconsulta i = new Interconsulta();
        i.pacienteId = p.id;
        i.pacienteNombre = p.nombreCompleto();
        i.atencionId = atencionId;
        i.solicitanteNombre = sesion.nombre();
        i.especialidad = especialidad;
        i.motivo = motivo;
        i.prioridad = prioridad == null ? "NORMAL" : prioridad;
        i = interconsultas.save(i);
        alertas.crear("ESPECIALISTA", "INTERCONSULTA", "URGENTE".equals(i.prioridad) ? "ALTA" : "MEDIA", p.id, p.nombreCompleto(),
            "Interconsulta a " + especialidad + ": " + motivo, "/especialista");
        auditoria.registrar("INTERCONSULTA", "/hce", p, especialidad);
        return i;
    }
}
