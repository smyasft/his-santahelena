package co.santahelena.his.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.santahelena.his.model.Agenda;
import co.santahelena.his.model.Cita;
import co.santahelena.his.model.OrdenLab;
import co.santahelena.his.model.Paciente;
import co.santahelena.his.model.Prescripcion;
import co.santahelena.his.model.Usuario;
import co.santahelena.his.repo.AgendaRepo;
import co.santahelena.his.repo.CitaRepo;
import co.santahelena.his.repo.OrdenLabRepo;
import co.santahelena.his.repo.PrescripcionRepo;
import co.santahelena.his.service.AgendaService;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.PacienteService;
import co.santahelena.his.service.SesionService;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.ReglaException;
import co.santahelena.his.util.Reglas;

/** Portal del paciente: solo ve y gestiona su propia información (cada consulta valida el dueño). */
@Controller
@RequestMapping("/portal")
public class PortalController {
    private final SesionService sesion;
    private final PacienteService pacientes;
    private final AgendaService agendaService;
    private final AgendaRepo agendas;
    private final CitaRepo citas;
    private final OrdenLabRepo ordenes;
    private final PrescripcionRepo prescripciones;
    private final AuditoriaService auditoria;

    public PortalController(SesionService sesion, PacienteService pacientes, AgendaService agendaService, AgendaRepo agendas, CitaRepo citas,
                            OrdenLabRepo ordenes, PrescripcionRepo prescripciones, AuditoriaService auditoria) {
        this.sesion = sesion;
        this.pacientes = pacientes;
        this.agendaService = agendaService;
        this.agendas = agendas;
        this.citas = citas;
        this.ordenes = ordenes;
        this.prescripciones = prescripciones;
        this.auditoria = auditoria;
    }

    private Paciente yo() {
        Usuario u = sesion.requerido();
        if (u.pacienteId == null) {
            throw new ReglaException("Su usuario no está vinculado a una historia clínica.");
        }
        return pacientes.obtener(u.pacienteId);
    }

    private List<OrdenLab> validadas(Paciente p) {
        List<OrdenLab> r = new ArrayList<>();
        for (OrdenLab o : ordenes.findByPacienteIdOrderByFechaDesc(p.id)) {
            if ("VALIDADA".equals(o.estado)) {
                r.add(o);
            }
        }
        return r;
    }

    @GetMapping
    public String inicio(Model model) {
        Paciente p = yo();
        LocalDateTime ahora = LocalDateTime.now();
        Cita proxima = null;
        for (Cita c : citas.findByPacienteIdOrderByFechaHoraDesc(p.id)) {
            if ("ASIGNADA".equals(c.estado) && c.fechaHora.isAfter(ahora)) {
                proxima = c;
            }
        }
        model.addAttribute("p", p);
        model.addAttribute("proxima", proxima);
        model.addAttribute("formulas", prescripciones.findByPacienteIdAndEstado(p.id, "ACTIVA"));
        List<OrdenLab> v = validadas(p);
        model.addAttribute("resultados", v.size() > 3 ? v.subList(0, 3) : v);
        return "portal/inicio";
    }

    @GetMapping("/citas")
    public String citas(@RequestParam(required = false) String agendaId, @RequestParam(required = false) String fecha, Model model) {
        Paciente p = yo();
        List<Cita> lista = citas.findByPacienteIdOrderByFechaHoraDesc(p.id);
        LocalDateTime ahora = LocalDateTime.now();
        model.addAttribute("p", p);
        model.addAttribute("lista", lista);
        model.addAttribute("ahora", ahora);
        List<String> cancelables = new ArrayList<>();
        for (Cita c : lista) {
            if ("ASIGNADA".equals(c.estado) && Reglas.puedeCancelarPorPortal(c.fechaHora, ahora)) {
                cancelables.add(c.id);
            }
        }
        model.addAttribute("cancelables", cancelables);
        model.addAttribute("agendas", agendas.findAllByOrderByProfesionalNombreAsc());
        LocalDate dia = Form.fecha(fecha);
        Agenda sel = agendaId == null ? null : agendaService.agenda(agendaId);
        model.addAttribute("agenda", sel);
        model.addAttribute("fecha", dia);
        model.addAttribute("franjas", sel == null || dia == null ? List.of() : agendaService.franjasLibres(sel, dia));
        model.addAttribute("hoy", LocalDate.now());
        return "portal/citas";
    }

    @PostMapping("/citas/agendar")
    public String agendar(@RequestParam String agendaId, @RequestParam String fechaHora, RedirectAttributes ra) {
        Paciente p = yo();
        Cita c = agendaService.asignar(p.id, agendaId, Form.fechaHora(fechaHora), "PORTAL", "Primera vez");
        ra.addFlashAttribute("ok", "Su cita quedó asignada para el " + c.fechaHora.toLocalDate() + " a las " + c.fechaHora.toLocalTime()
            + " con " + c.profesionalNombre + " (" + c.consultorio + ").");
        return "redirect:/portal/citas";
    }

    @PostMapping("/citas/{id}/cancelar")
    public String cancelar(@PathVariable String id, RedirectAttributes ra) {
        agendaService.cancelar(id, "Cancelada por el paciente en el portal", yo().id);
        ra.addFlashAttribute("ok", "La cita fue cancelada.");
        return "redirect:/portal/citas";
    }

    @GetMapping("/resultados")
    public String resultados(Model model) {
        Paciente p = yo();
        model.addAttribute("p", p);
        model.addAttribute("lista", validadas(p));
        return "portal/resultados";
    }

    @GetMapping("/resultados/{id}")
    public String resultado(@PathVariable String id, Model model) {
        Paciente p = yo();
        OrdenLab o = ordenes.findById(id).orElseThrow(() -> new ReglaException("Resultado no encontrado."));
        if (!p.id.equals(o.pacienteId) || !"VALIDADA".equals(o.estado)) {
            throw new ReglaException("El resultado no está disponible.");
        }
        auditoria.registrar("RESULTADO_DESCARGADO", "/portal", p, o.codigoMuestra);
        model.addAttribute("p", p);
        model.addAttribute("o", o);
        return "imprimir/resultado";
    }

    @GetMapping("/formulas")
    public String formulas(Model model) {
        Paciente p = yo();
        model.addAttribute("p", p);
        model.addAttribute("lista", prescripciones.findByPacienteIdOrderByFechaDesc(p.id));
        return "portal/formulas";
    }

    @GetMapping("/formulas/{id}")
    public String formula(@PathVariable String id, Model model) {
        Paciente p = yo();
        Prescripcion pr = prescripciones.findById(id).orElseThrow(() -> new ReglaException("Fórmula no encontrada."));
        if (!p.id.equals(pr.pacienteId)) {
            throw new ReglaException("La fórmula no está disponible.");
        }
        auditoria.registrar("FORMULA_DESCARGADA", "/portal", p, pr.id);
        model.addAttribute("p", p);
        model.addAttribute("pr", pr);
        return "imprimir/formula";
    }
}
