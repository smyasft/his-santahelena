package co.santahelena.his.web;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.santahelena.his.model.Admision;
import co.santahelena.his.model.Administracion;
import co.santahelena.his.model.Paciente;
import co.santahelena.his.model.Prescripcion;
import co.santahelena.his.model.SignosVitales;
import co.santahelena.his.model.Triaje;
import co.santahelena.his.repo.AdministracionRepo;
import co.santahelena.his.repo.AdmisionRepo;
import co.santahelena.his.repo.PrescripcionRepo;
import co.santahelena.his.repo.SignosRepo;
import co.santahelena.his.repo.TriajeRepo;
import co.santahelena.his.service.AlertaService;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.ConfigService;
import co.santahelena.his.service.PacienteService;
import co.santahelena.his.service.SesionService;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.ReglaException;
import co.santahelena.his.util.Reglas;

@Controller
@RequestMapping("/enfermeria")
public class EnfermeriaController {
    private final AdmisionRepo admisiones;
    private final SignosRepo signos;
    private final TriajeRepo triajes;
    private final PrescripcionRepo prescripciones;
    private final AdministracionRepo administraciones;
    private final PacienteService pacientes;
    private final AlertaService alertas;
    private final AuditoriaService auditoria;
    private final ConfigService config;
    private final SesionService sesion;

    public EnfermeriaController(AdmisionRepo admisiones, SignosRepo signos, TriajeRepo triajes, PrescripcionRepo prescripciones,
                                AdministracionRepo administraciones, PacienteService pacientes, AlertaService alertas,
                                AuditoriaService auditoria, ConfigService config, SesionService sesion) {
        this.admisiones = admisiones;
        this.signos = signos;
        this.triajes = triajes;
        this.prescripciones = prescripciones;
        this.administraciones = administraciones;
        this.pacientes = pacientes;
        this.alertas = alertas;
        this.auditoria = auditoria;
        this.config = config;
        this.sesion = sesion;
    }

    /** Cola de urgencias: primero los que esperan triaje y luego por nivel y hora de llegada. */
    @GetMapping
    public String cola(Model model) {
        List<Admision> lista = admisiones.findByTipoAndEstadoInOrderByFechaIngresoAsc("URGENCIAS", List.of("EN_ESPERA_TRIAJE", "TRIADO", "EN_ATENCION"));
        lista.sort(Comparator.comparing((Admision a) -> a.triajeNivel == null ? 0 : a.triajeNivel).thenComparing(a -> a.fechaIngreso));
        model.addAttribute("lista", lista);
        model.addAttribute("ahora", LocalDateTime.now());
        return "enfermeria/cola";
    }

    private SignosVitales leerSignos(Map<String, String> f) {
        SignosVitales s = new SignosVitales();
        s.taSistolica = Form.i(f.get("taSistolica"));
        s.taDiastolica = Form.i(f.get("taDiastolica"));
        s.fc = Form.i(f.get("fc"));
        s.fr = Form.i(f.get("fr"));
        s.temperatura = Form.d(f.get("temperatura"));
        s.saturacion = Form.i(f.get("saturacion"));
        s.glucometria = Form.i(f.get("glucometria"));
        return s;
    }

    @GetMapping("/triaje/{admisionId}")
    public String triaje(@PathVariable String admisionId, Model model) {
        Admision a = admisiones.findById(admisionId).orElseThrow(() -> new ReglaException("Ingreso no encontrado."));
        model.addAttribute("a", a);
        model.addAttribute("p", pacientes.obtener(a.pacienteId));
        model.addAttribute("s", new SignosVitales());
        model.addAttribute("motivo", a.motivo);
        return "enfermeria/triaje";
    }

    @PostMapping("/triaje/{admisionId}")
    public String guardarTriaje(@PathVariable String admisionId, @RequestParam Map<String, String> f, Model model, RedirectAttributes ra) {
        Admision a = admisiones.findById(admisionId).orElseThrow(() -> new ReglaException("Ingreso no encontrado."));
        Paciente p = pacientes.obtener(a.pacienteId);
        SignosVitales s = leerSignos(f);
        String motivo = Form.s(f.get("motivo"));
        Integer nivel = Form.i(f.get("nivel"));
        String error = Reglas.valorImposible(s);
        if (error == null && (s.taSistolica == null || s.fc == null || s.fr == null || s.temperatura == null || s.saturacion == null)) {
            error = "Registre al menos TA, FC, FR, temperatura y saturación.";
        }
        int sugerido = Reglas.sugerirNivel(s);
        boolean guardar = "guardar".equals(f.get("accion"));
        if (guardar && error == null) {
            if (nivel == null || nivel < 1 || nivel > 5) {
                error = "Seleccione el nivel de triaje definitivo.";
            } else if (nivel > sugerido && Form.s(f.get("justificacion")) == null) {
                error = "Clasificó con menor prioridad que la sugerida por los signos (" + Reglas.nombreNivel(sugerido) + "). Escriba la justificación.";
            } else if (!"EN_ESPERA_TRIAJE".equals(a.estado) && !"TRIADO".equals(a.estado)) {
                error = "El paciente ya está en atención médica.";
            }
        }
        if (!guardar || error != null) {
            model.addAttribute("a", a);
            model.addAttribute("p", p);
            model.addAttribute("s", s);
            model.addAttribute("motivo", motivo);
            model.addAttribute("sugerido", Reglas.valorImposible(s) == null ? sugerido : null);
            model.addAttribute("nivelElegido", nivel);
            model.addAttribute("justificacion", f.get("justificacion"));
            model.addAttribute("alertasSignos", Reglas.alertasSignos(s, config.get()));
            model.addAttribute("mensajeError", error);
            return "enfermeria/triaje";
        }
        s.pacienteId = p.id;
        s.admisionId = a.id;
        s.usuario = sesion.nombre();
        s.alertas = Reglas.alertasSignos(s, config.get());
        s = signos.save(s);
        Triaje t = new Triaje();
        t.admisionId = a.id;
        t.pacienteId = p.id;
        t.pacienteNombre = p.nombreCompleto();
        t.nivel = nivel;
        t.nivelSugerido = sugerido;
        t.motivo = motivo + (Form.s(f.get("justificacion")) == null ? "" : " | Justificación: " + f.get("justificacion"));
        t.signosId = s.id;
        t.resumenSignos = "TA " + s.ta() + " FC " + s.fc + " FR " + s.fr + " T " + s.temperatura + " SatO2 " + s.saturacion;
        t.enfermero = sesion.nombre();
        triajes.save(t);
        a.triajeNivel = nivel;
        a.triajeFecha = LocalDateTime.now();
        a.estado = "TRIADO";
        if (motivo != null) {
            a.motivo = motivo;
        }
        admisiones.save(a);
        if (nivel <= 2) {
            alertas.crear("MEDICO", "TRIAJE", "ALTA", p.id, p.nombreCompleto(),
                "Paciente clasificado " + Reglas.nombreNivel(nivel) + " en urgencias: " + a.motivo, "/hce");
        }
        auditoria.registrar("TRIAJE", "/enfermeria", p, Reglas.nombreNivel(nivel) + " (sugerido " + sugerido + ")");
        ra.addFlashAttribute("ok", p.nombreCompleto() + " clasificado como " + Reglas.nombreNivel(nivel) + ".");
        return "redirect:/enfermeria";
    }

    @GetMapping("/pacientes")
    public String pacientes(Model model) {
        model.addAttribute("hospitalizados", admisiones.findByEstadoOrderByFechaIngresoAsc("EN_CAMA"));
        model.addAttribute("urgencias", admisiones.findByTipoAndEstadoInOrderByFechaIngresoAsc("URGENCIAS", List.of("TRIADO", "EN_ATENCION")));
        return "enfermeria/pacientes";
    }

    @GetMapping("/paciente/{id}")
    public String paciente(@PathVariable String id, Model model) {
        Paciente p = pacientes.obtener(id);
        auditoria.registrar("HC_CONSULTADA", "/enfermeria/paciente", p, "Registro de enfermería");
        List<Prescripcion> activas = new ArrayList<>();
        for (Prescripcion pr : prescripciones.findByPacienteIdOrderByFechaDesc(id)) {
            if ("ACTIVA".equals(pr.estado) || "DISPENSADA".equals(pr.estado)) {
                activas.add(pr);
            }
        }
        Admision actual = null;
        for (Admision a : admisiones.findByPacienteIdOrderByFechaIngresoDesc(id)) {
            if (!"EGRESADO".equals(a.estado) && !"ATENDIDO".equals(a.estado)) {
                actual = a;
                break;
            }
        }
        model.addAttribute("p", p);
        model.addAttribute("admision", actual);
        model.addAttribute("signos", signos.findByPacienteIdOrderByFechaDesc(id));
        model.addAttribute("prescripciones", activas);
        model.addAttribute("administraciones", administraciones.findByPacienteIdOrderByFechaDesc(id));
        model.addAttribute("cfg2", config.get());
        return "enfermeria/paciente";
    }

    @PostMapping("/paciente/{id}/signos")
    public String registrarSignos(@PathVariable String id, @RequestParam Map<String, String> f, RedirectAttributes ra) {
        Paciente p = pacientes.obtener(id);
        SignosVitales s = leerSignos(f);
        String error = Reglas.valorImposible(s);
        if (error != null) {
            throw new ReglaException(error);
        }
        if (s.taSistolica == null && s.fc == null && s.fr == null && s.temperatura == null && s.saturacion == null && s.glucometria == null) {
            throw new ReglaException("Registre al menos un signo vital.");
        }
        s.pacienteId = p.id;
        s.admisionId = Form.s(f.get("admisionId"));
        s.usuario = sesion.nombre();
        s.alertas = Reglas.alertasSignos(s, config.get());
        signos.save(s);
        if (!s.alertas.isEmpty()) {
            alertas.crear("MEDICO", "SIGNOS_FUERA_RANGO", "ALTA", p.id, p.nombreCompleto(), String.join(" · ", s.alertas), "/hce/historia/" + p.id);
        }
        auditoria.registrar("SIGNOS_VITALES", "/enfermeria", p, s.alertas.isEmpty() ? "Normales" : String.join("; ", s.alertas));
        ra.addFlashAttribute(s.alertas.isEmpty() ? "ok" : "error", s.alertas.isEmpty() ? "Signos registrados." : "Signos registrados con alertas: " + String.join(" · ", s.alertas) + ". Se notificó al médico.");
        return "redirect:/enfermeria/paciente/" + id;
    }

    /** Registro de administración (US-020): si dosis o vía difieren de la orden se exige confirmación. */
    @PostMapping("/paciente/{id}/administrar")
    public String administrar(@PathVariable String id, @RequestParam Map<String, String> f, RedirectAttributes ra) {
        Paciente p = pacientes.obtener(id);
        Prescripcion pr = prescripciones.findById(f.get("prescripcionId")).orElseThrow(() -> new ReglaException("Fórmula no encontrada."));
        if (!pr.pacienteId.equals(p.id)) {
            throw new ReglaException("La fórmula no corresponde al paciente.");
        }
        Integer idx = Form.i(f.get("item"));
        if (idx == null || idx < 0 || idx >= pr.items.size()) {
            throw new ReglaException("Seleccione el medicamento.");
        }
        Prescripcion.Item it = pr.items.get(idx);
        String dosis = Form.s(f.get("dosis"));
        String via = Form.s(f.get("via"));
        if (dosis == null || via == null) {
            throw new ReglaException("Registre dosis y vía administradas.");
        }
        String alerta = null;
        if (!Form.norm(dosis).equals(Form.norm(it.dosis)) || !Form.norm(via).equals(Form.norm(it.via))) {
            alerta = "Difiere de la orden (" + it.dosis + " " + it.via + ")";
            if (!Form.si(f.get("confirmar"))) {
                throw new ReglaException("La dosis o la vía no coinciden con la orden médica (" + it.dosis + " vía " + it.via
                    + "). Si es correcto, marque «Confirmo la diferencia» y registre de nuevo.");
            }
        }
        Administracion ad = new Administracion();
        ad.pacienteId = p.id;
        ad.prescripcionId = pr.id;
        ad.itemIndice = idx;
        ad.medicamento = it.nombre;
        ad.dosisOrdenada = it.dosis;
        ad.viaOrdenada = it.via;
        ad.dosis = dosis;
        ad.via = via;
        ad.observacion = Form.s(f.get("observacion"));
        ad.enfermero = sesion.nombre();
        ad.alerta = alerta;
        administraciones.save(ad);
        auditoria.registrar("ADMINISTRACION_MED", "/enfermeria", p, it.nombre + " " + dosis + " " + via + (alerta == null ? "" : " – " + alerta));
        ra.addFlashAttribute("ok", "Administración de " + it.nombre + " registrada.");
        return "redirect:/enfermeria/paciente/" + id;
    }

    @PostMapping("/administracion/{id}/anular")
    public String anular(@PathVariable String id, @RequestParam(required = false) String justificacion, RedirectAttributes ra) {
        Administracion ad = administraciones.findById(id).orElseThrow(() -> new ReglaException("Registro no encontrado."));
        if (ad.anulada) {
            throw new ReglaException("El registro ya estaba anulado.");
        }
        if (Form.s(justificacion) == null) {
            throw new ReglaException("Escriba la justificación de la anulación.");
        }
        ad.anulada = true;
        ad.justificacionAnulacion = justificacion;
        ad.anuladaPor = sesion.nombre();
        ad.anuladaEn = LocalDateTime.now();
        administraciones.save(ad);
        auditoria.registrar("ADMINISTRACION_ANULADA", "/enfermeria", ad.pacienteId, null, ad.medicamento + ": " + justificacion);
        ra.addFlashAttribute("ok", "Registro anulado (se conserva en la historia con la justificación).");
        return "redirect:/enfermeria/paciente/" + ad.pacienteId;
    }
}
