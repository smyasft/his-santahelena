package co.santahelena.his.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.santahelena.his.model.Admision;
import co.santahelena.his.model.Atencion;
import co.santahelena.his.model.CatalogoItem;
import co.santahelena.his.model.Cita;
import co.santahelena.his.model.Paciente;
import co.santahelena.his.model.Prescripcion;
import co.santahelena.his.model.Usuario;
import co.santahelena.his.repo.AdmisionRepo;
import co.santahelena.his.repo.AtencionRepo;
import co.santahelena.his.repo.CatalogoRepo;
import co.santahelena.his.repo.CitaRepo;
import co.santahelena.his.repo.ExamenLabRepo;
import co.santahelena.his.repo.InterconsultaRepo;
import co.santahelena.his.repo.MedicamentoRepo;
import co.santahelena.his.repo.OrdenLabRepo;
import co.santahelena.his.repo.PrescripcionRepo;
import co.santahelena.his.repo.SignosRepo;
import co.santahelena.his.repo.TriajeRepo;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.HceService;
import co.santahelena.his.service.LabService;
import co.santahelena.his.service.PacienteService;
import co.santahelena.his.service.SesionService;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.ReglaException;

@Controller
@RequestMapping("/hce")
public class HceController {
    /** Campos adicionales por especialidad (US-036). */
    public static final Map<String, List<String>> PLANTILLAS = new LinkedHashMap<>();

    static {
        PLANTILLAS.put("Medicina Interna", List.of("Revisión por sistemas", "Escalas de riesgo (CURB-65, Wells, CHA2DS2-VASc)", "Conciliación de medicamentos", "Plan de seguimiento"));
        PLANTILLAS.put("Pediatría", List.of("Peso (kg)", "Talla (cm)", "Percentil peso/talla", "Esquema de vacunación", "Desarrollo psicomotor"));
        PLANTILLAS.put("Ginecología", List.of("Fecha de última menstruación", "Gestas / Partos / Cesáreas / Abortos", "Método de planificación", "Citología más reciente"));
        PLANTILLAS.put("Cirugía General", List.of("Abdomen (hallazgos)", "Indicación quirúrgica", "Riesgo quirúrgico (ASA)", "Plan preoperatorio"));
        PLANTILLAS.put("Ortopedia", List.of("Segmento afectado", "Arcos de movimiento", "Pruebas especiales", "Imágenes revisadas"));
    }

    public static final List<String> SERVICIOS = List.of("Medicina Interna", "Cirugía", "Pediatría", "UCI Adultos");
    public static final List<String> ESPECIALIDADES = List.of("Medicina Interna", "Pediatría", "Ginecología", "Cirugía General", "Ortopedia", "Cardiología", "Neurología");

    private final SesionService sesion;
    private final PacienteService pacientes;
    private final HceService hce;
    private final LabService lab;
    private final AtencionRepo atenciones;
    private final CitaRepo citas;
    private final AdmisionRepo admisiones;
    private final SignosRepo signos;
    private final TriajeRepo triajes;
    private final PrescripcionRepo prescripciones;
    private final OrdenLabRepo ordenes;
    private final InterconsultaRepo interconsultas;
    private final MedicamentoRepo medicamentos;
    private final ExamenLabRepo examenes;
    private final CatalogoRepo catalogos;
    private final AuditoriaService auditoria;

    public HceController(SesionService sesion, PacienteService pacientes, HceService hce, LabService lab, AtencionRepo atenciones, CitaRepo citas,
                         AdmisionRepo admisiones, SignosRepo signos, TriajeRepo triajes, PrescripcionRepo prescripciones, OrdenLabRepo ordenes,
                         InterconsultaRepo interconsultas, MedicamentoRepo medicamentos, ExamenLabRepo examenes, CatalogoRepo catalogos,
                         AuditoriaService auditoria) {
        this.sesion = sesion;
        this.pacientes = pacientes;
        this.hce = hce;
        this.lab = lab;
        this.atenciones = atenciones;
        this.citas = citas;
        this.admisiones = admisiones;
        this.signos = signos;
        this.triajes = triajes;
        this.prescripciones = prescripciones;
        this.ordenes = ordenes;
        this.interconsultas = interconsultas;
        this.medicamentos = medicamentos;
        this.examenes = examenes;
        this.catalogos = catalogos;
        this.auditoria = auditoria;
    }

    @GetMapping
    public String agenda(Model model) {
        Usuario u = sesion.requerido();
        LocalDate hoy = LocalDate.now();
        List<Cita> mis = citas.findByProfesionalIdAndFechaHoraBetween(u.id, hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
        mis.sort(Comparator.comparing(c -> c.fechaHora));
        List<Admision> urg = admisiones.findByTipoAndEstadoInOrderByFechaIngresoAsc("URGENCIAS", List.of("TRIADO", "EN_ATENCION"));
        urg.sort(Comparator.comparing((Admision a) -> a.triajeNivel == null ? 9 : a.triajeNivel).thenComparing(a -> a.fechaIngreso));
        model.addAttribute("citas", mis);
        model.addAttribute("urgencias", urg);
        model.addAttribute("hospitalizados", admisiones.findByEstadoOrderByFechaIngresoAsc("EN_CAMA"));
        model.addAttribute("borradores", atenciones.findByProfesionalIdAndFirmadaFalseOrderByFechaDesc(u.id));
        return "hce/agenda";
    }

    @GetMapping("/buscar")
    public String buscar(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("lista", Form.s(q) == null ? List.of() : pacientes.buscar(q, 30));
        model.addAttribute("q", q);
        return "hce/buscar";
    }

    @GetMapping("/historia/{id}")
    public String historia(@PathVariable String id, Model model) {
        Paciente p = pacientes.obtener(id);
        auditoria.registrar("HC_CONSULTADA", "/hce/historia", p, "Historia clínica completa");
        model.addAttribute("p", p);
        model.addAttribute("atenciones", atenciones.findByPacienteIdOrderByFechaDesc(id));
        model.addAttribute("signos", signos.findByPacienteIdOrderByFechaDesc(id));
        model.addAttribute("triajes", triajes.findByPacienteIdOrderByFechaDesc(id));
        model.addAttribute("prescripciones", prescripciones.findByPacienteIdOrderByFechaDesc(id));
        model.addAttribute("ordenes", ordenes.findByPacienteIdOrderByFechaDesc(id));
        model.addAttribute("interconsultas", interconsultas.findByPacienteIdOrderByFechaDesc(id));
        List<Admision> adm = admisiones.findByPacienteIdOrderByFechaIngresoDesc(id);
        Admision activa = null;
        for (Admision a : adm) {
            if (!"EGRESADO".equals(a.estado) && !"ATENDIDO".equals(a.estado)) {
                activa = a;
                break;
            }
        }
        model.addAttribute("admisiones", adm);
        model.addAttribute("activa", activa);
        return "hce/historia";
    }

    @PostMapping("/atencion/nueva")
    public String nueva(@RequestParam String pacienteId, @RequestParam(defaultValue = "CONSULTA") String tipo,
                        @RequestParam(required = false) String citaId, @RequestParam(required = false) String admisionId,
                        @RequestParam(required = false) String interconsultaId) {
        Paciente p = pacientes.obtener(pacienteId);
        Admision adm = Form.s(admisionId) == null ? null : admisiones.findById(admisionId).orElse(null);
        if (adm != null && "URGENCIAS".equals(adm.tipo)) {
            if ("EN_ESPERA_TRIAJE".equals(adm.estado)) {
                throw new ReglaException("El paciente aún no ha sido clasificado en triaje.");
            }
            adm.estado = "EN_ATENCION";
            admisiones.save(adm);
            tipo = "URGENCIAS";
        }
        Atencion a = hce.nueva(p, tipo, Form.s(citaId), adm == null ? null : adm.id, Form.s(interconsultaId));
        if (adm != null && "URGENCIAS".equals(adm.tipo)) {
            a.motivoConsulta = adm.motivo;
            atenciones.save(a);
        }
        auditoria.registrar("HC_ABIERTA", "/hce/atencion", p, a.tipo);
        return "redirect:/hce/atencion/" + a.id;
    }

    @GetMapping("/atencion/{id}")
    public String atencion(@PathVariable String id, Model model) {
        Atencion a = hce.atencion(id);
        Paciente p = pacientes.obtener(a.pacienteId);
        Usuario u = sesion.requerido();
        model.addAttribute("a", a);
        model.addAttribute("p", p);
        model.addAttribute("editable", !a.firmada && u.id.equals(a.profesionalId));
        model.addAttribute("aclaratorias", atenciones.findByAclaraA(a.id));
        model.addAttribute("ultimosSignos", signos.findByPacienteIdOrderByFechaDesc(p.id).stream().limit(3).toList());
        model.addAttribute("servicios", SERVICIOS);
        model.addAttribute("hospitalizado", admisiones.findByPacienteIdOrderByFechaIngresoDesc(p.id).stream()
            .anyMatch(x -> "HOSPITALIZACION".equals(x.tipo) && "EN_CAMA".equals(x.estado)));
        if (!a.firmada && "ESPECIALIDAD".equals(a.tipo) && a.camposEspecialidad.isEmpty()) {
            for (String etiqueta : PLANTILLAS.getOrDefault(a.especialidad, List.of("Hallazgos de la especialidad", "Concepto del especialista"))) {
                Atencion.Campo c = new Atencion.Campo();
                c.etiqueta = etiqueta;
                a.camposEspecialidad.add(c);
            }
        }
        if (!a.firmada) {
            auditoria.registrar("HC_EDICION", "/hce/atencion", p, a.tipo);
        }
        return "hce/atencion";
    }

    @PostMapping("/atencion/{id}/guardar")
    public String guardar(@PathVariable String id, @RequestParam MultiValueMap<String, String> f, RedirectAttributes ra) {
        Atencion a = hce.atencion(id);
        hce.verificarEditable(a);
        a.motivoConsulta = F.v(f, "motivoConsulta");
        a.enfermedadActual = F.v(f, "enfermedadActual");
        a.antecedentes = F.v(f, "antecedentes");
        a.examenFisico = F.v(f, "examenFisico");
        a.analisis = F.v(f, "analisis");
        a.plan = F.v(f, "plan");
        a.diagnosticos.clear();
        List<String> codigos = F.l(f, "dx_codigo");
        List<String> tipos = F.l(f, "dx_tipo");
        List<String> invalidos = new ArrayList<>();
        for (int i = 0; i < codigos.size(); i++) {
            String cod = F.at(codigos, i);
            if (cod == null) {
                continue;
            }
            cod = cod.split(" ")[0].trim().toUpperCase();
            CatalogoItem c = catalogos.findByTipoAndCodigo("CIE10", cod).orElse(null);
            if (c == null) {
                invalidos.add(cod);
                continue;
            }
            Atencion.Diagnostico d = new Atencion.Diagnostico();
            d.codigo = c.codigo;
            d.descripcion = c.descripcion;
            d.tipo = F.at(tipos, i) == null ? (a.diagnosticos.isEmpty() ? "Principal" : "Relacionado") : F.at(tipos, i);
            a.diagnosticos.add(d);
        }
        a.camposEspecialidad.clear();
        List<String> et = F.l(f, "campo_etiqueta");
        List<String> va = F.l(f, "campo_valor");
        for (int i = 0; i < et.size(); i++) {
            Atencion.Campo c = new Atencion.Campo();
            c.etiqueta = F.at(et, i);
            c.valor = F.at(va, i);
            a.camposEspecialidad.add(c);
        }
        a.ordenHospitalizacion = Form.si(F.v(f, "ordenHospitalizacion"));
        a.servicioHospitalizacion = a.ordenHospitalizacion ? F.v(f, "servicioHospitalizacion") : null;
        a.requiereAislamiento = a.ordenHospitalizacion && Form.si(F.v(f, "requiereAislamiento"));
        a.ordenEgreso = Form.si(F.v(f, "ordenEgreso"));
        hce.guardar(a);
        if (!invalidos.isEmpty()) {
            throw new ReglaException("Se guardó el borrador, pero estos códigos no existen en CIE-10: " + String.join(", ", invalidos)
                + ". Corríjalos antes de firmar.");
        }
        if ("firmar".equals(F.v(f, "accion"))) {
            hce.firmar(a.id);
            ra.addFlashAttribute("ok", "Registro firmado electrónicamente. Ya no admite modificaciones.");
        } else {
            ra.addFlashAttribute("ok", "Borrador guardado.");
        }
        return "redirect:/hce/atencion/" + a.id;
    }

    @PostMapping("/atencion/{id}/aclaracion")
    public String aclaracion(@PathVariable String id, @RequestParam(required = false) String texto, RedirectAttributes ra) {
        hce.aclaratoria(id, Form.s(texto));
        ra.addFlashAttribute("ok", "Nota aclaratoria firmada y vinculada al registro original.");
        return "redirect:/hce/atencion/" + id;
    }

    @GetMapping("/atencion/{id}/imprimir")
    public String imprimir(@PathVariable String id, Model model) {
        Atencion a = hce.atencion(id);
        Paciente p = pacientes.obtener(a.pacienteId);
        auditoria.registrar("HC_IMPRESA", "/hce/atencion", p, a.tipo);
        model.addAttribute("a", a);
        model.addAttribute("p", p);
        model.addAttribute("aclaratorias", atenciones.findByAclaraA(a.id));
        return "imprimir/historia";
    }

    // ------------------------------------------------------------------ fórmula médica

    private void formulaModelo(Model model, Paciente p, String atencionId) {
        model.addAttribute("p", p);
        model.addAttribute("atencionId", atencionId);
        model.addAttribute("medicamentos", medicamentos.findByActivoTrueOrderByNombreAsc());
        model.addAttribute("activas", prescripciones.findByPacienteIdAndEstado(p.id, "ACTIVA"));
        model.addAttribute("filas", List.of(0, 1, 2, 3, 4, 5));
    }

    @GetMapping("/prescripcion/nueva")
    public String nuevaFormula(@RequestParam String pacienteId, @RequestParam(required = false) String atencionId, Model model) {
        formulaModelo(model, pacientes.obtener(pacienteId), atencionId);
        model.addAttribute("f", new LinkedHashMap<String, String>());
        return "hce/prescripcion";
    }

    @PostMapping("/prescripcion")
    public String formula(@RequestParam Map<String, String> f, Model model, RedirectAttributes ra) {
        Paciente p = pacientes.obtener(f.get("pacienteId"));
        String atencionId = Form.s(f.get("atencionId"));
        List<Prescripcion.Item> items = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String med = Form.s(f.get("med_" + i));
            if (med == null) {
                continue;
            }
            Prescripcion.Item it = new Prescripcion.Item();
            it.medicamentoId = med;
            it.dosis = Form.s(f.get("dosis_" + i));
            it.via = Form.s(f.get("via_" + i));
            it.frecuencia = Form.s(f.get("frecuencia_" + i));
            Integer dias = Form.i(f.get("dias_" + i));
            Integer cant = Form.i(f.get("cantidad_" + i));
            it.dias = dias == null ? 0 : dias;
            it.cantidad = cant == null ? 0 : cant;
            it.justificacionControl = Form.s(f.get("just_" + i));
            items.add(it);
        }
        List<String> alertas = hce.alertasFormula(p, items);
        if ("firmar".equals(f.get("accion"))) {
            try {
                hce.prescribir(p, atencionId, items, Form.s(f.get("justificacion")));
                ra.addFlashAttribute("ok", "Fórmula médica firmada y enviada a farmacia.");
                return atencionId == null ? "redirect:/hce/historia/" + p.id : "redirect:/hce/atencion/" + atencionId;
            } catch (ReglaException e) {
                model.addAttribute("mensajeError", e.getMessage());
            }
        }
        formulaModelo(model, p, atencionId);
        model.addAttribute("f", f);
        model.addAttribute("alertas", alertas);
        model.addAttribute("revisada", true);
        return "hce/prescripcion";
    }

    @GetMapping("/formula/{id}/imprimir")
    public String imprimirFormula(@PathVariable String id, Model model) {
        Prescripcion pr = prescripciones.findById(id).orElseThrow(() -> new ReglaException("Fórmula no encontrada."));
        model.addAttribute("pr", pr);
        model.addAttribute("p", pacientes.obtener(pr.pacienteId));
        return "imprimir/formula";
    }

    // ------------------------------------------------------------------ laboratorio e interconsulta

    @GetMapping("/orden-lab/nueva")
    public String nuevaOrden(@RequestParam String pacienteId, @RequestParam(required = false) String atencionId, Model model) {
        model.addAttribute("p", pacientes.obtener(pacienteId));
        model.addAttribute("atencionId", atencionId);
        model.addAttribute("examenes", examenes.findByActivoTrueOrderByNombreAsc());
        return "hce/orden-lab";
    }

    @PostMapping("/orden-lab")
    public String crearOrden(@RequestParam MultiValueMap<String, String> f, RedirectAttributes ra) {
        Paciente p = pacientes.obtener(F.v(f, "pacienteId"));
        Usuario u = sesion.requerido();
        lab.crear(p, F.v(f, "atencionId"), F.l(f, "examenes"), F.v(f, "prioridad"), F.v(f, "observaciones"), u.id, u.nombre);
        ra.addFlashAttribute("ok", "Orden de laboratorio enviada.");
        String at = F.v(f, "atencionId");
        return at == null ? "redirect:/hce/historia/" + p.id : "redirect:/hce/atencion/" + at;
    }

    @GetMapping("/interconsulta/nueva")
    public String nuevaInterconsulta(@RequestParam String pacienteId, @RequestParam(required = false) String atencionId, Model model) {
        model.addAttribute("p", pacientes.obtener(pacienteId));
        model.addAttribute("atencionId", atencionId);
        model.addAttribute("especialidades", ESPECIALIDADES);
        return "hce/interconsulta";
    }

    @PostMapping("/interconsulta")
    public String crearInterconsulta(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        Paciente p = pacientes.obtener(f.get("pacienteId"));
        String at = Form.s(f.get("atencionId"));
        hce.solicitarInterconsulta(p, at, Form.s(f.get("especialidad")), Form.s(f.get("motivo")), Form.s(f.get("prioridad")));
        ra.addFlashAttribute("ok", "Interconsulta enviada a " + f.get("especialidad") + ".");
        return at == null ? "redirect:/hce/historia/" + p.id : "redirect:/hce/atencion/" + at;
    }
}
