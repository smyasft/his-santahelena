package co.santahelena.his.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import co.santahelena.his.model.Agenda;
import co.santahelena.his.model.Cita;
import co.santahelena.his.model.Eps;
import co.santahelena.his.model.Paciente;
import co.santahelena.his.repo.AdmisionRepo;
import co.santahelena.his.repo.AgendaRepo;
import co.santahelena.his.repo.CitaRepo;
import co.santahelena.his.repo.EpsRepo;
import co.santahelena.his.service.AdresService;
import co.santahelena.his.service.AgendaService;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.PacienteService;
import co.santahelena.his.service.SesionService;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.ReglaException;

@Controller
@RequestMapping("/admisiones")
public class AdmisionesController {
    private final PacienteService pacientes;
    private final AdresService adres;
    private final AgendaService agendaService;
    private final EpsRepo eps;
    private final AgendaRepo agendas;
    private final CitaRepo citas;
    private final AdmisionRepo admisiones;
    private final AuditoriaService auditoria;
    private final SesionService sesion;

    public AdmisionesController(PacienteService pacientes, AdresService adres, AgendaService agendaService, EpsRepo eps, AgendaRepo agendas,
                                CitaRepo citas, AdmisionRepo admisiones, AuditoriaService auditoria, SesionService sesion) {
        this.pacientes = pacientes;
        this.adres = adres;
        this.agendaService = agendaService;
        this.eps = eps;
        this.agendas = agendas;
        this.citas = citas;
        this.admisiones = admisiones;
        this.auditoria = auditoria;
        this.sesion = sesion;
    }

    @GetMapping
    public String buscar(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("lista", pacientes.buscar(q, 50));
        model.addAttribute("q", q);
        return "admisiones/buscar";
    }

    @GetMapping("/paciente/nuevo")
    public String nuevo(@RequestParam(required = false) String documento, Model model) {
        Paciente p = new Paciente();
        p.tipoDocumento = "CC";
        p.numeroDocumento = documento;
        model.addAttribute("p", p);
        model.addAttribute("epsLista", eps.findByActivoTrueOrderByNombreAsc());
        return "admisiones/paciente-form";
    }

    @GetMapping("/paciente/{id}/editar")
    public String editar(@PathVariable String id, Model model) {
        model.addAttribute("p", pacientes.obtener(id));
        model.addAttribute("epsLista", eps.findByActivoTrueOrderByNombreAsc());
        return "admisiones/paciente-form";
    }

    @PostMapping("/paciente/guardar")
    public String guardar(@RequestParam Map<String, String> f, Model model, RedirectAttributes ra) {
        String id = Form.s(f.get("id"));
        boolean nuevo = id == null;
        Paciente p = nuevo ? new Paciente() : pacientes.obtener(id);
        p.tipoDocumento = Form.s(f.get("tipoDocumento"));
        p.numeroDocumento = Form.s(f.get("numeroDocumento"));
        if (p.numeroDocumento != null) {
            p.numeroDocumento = p.numeroDocumento.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
        }
        p.nombres = Form.s(f.get("nombres"));
        p.apellidos = Form.s(f.get("apellidos"));
        p.fechaNacimiento = Form.fecha(f.get("fechaNacimiento"));
        p.sexo = Form.s(f.get("sexo"));
        p.telefono = Form.s(f.get("telefono"));
        p.email = Form.s(f.get("email"));
        p.direccion = Form.s(f.get("direccion"));
        p.municipio = Form.s(f.get("municipio"));
        p.regimen = Form.s(f.get("regimen"));
        p.nivel = Form.s(f.get("nivel"));
        p.acompananteNombre = Form.s(f.get("acompananteNombre"));
        p.acompananteTelefono = Form.s(f.get("acompananteTelefono"));
        p.acompananteParentesco = Form.s(f.get("acompananteParentesco"));
        String epsId = Form.s(f.get("epsId"));
        if (epsId == null) {
            p.epsId = null;
            p.epsNombre = "Particular";
        } else {
            Eps e = eps.findById(epsId).orElseThrow(() -> new ReglaException("EPS no válida."));
            p.epsId = e.id;
            p.epsNombre = e.nombre;
        }
        List<String> al = new ArrayList<>();
        String alergias = Form.s(f.get("alergias"));
        if (alergias != null) {
            for (String a : alergias.split("[,;]")) {
                if (!a.isBlank()) {
                    al.add(a.trim());
                }
            }
        }
        p.alergias = al;
        try {
            if (p.fechaNacimiento != null && p.fechaNacimiento.isAfter(LocalDate.now().minusYears(18)) && p.acompananteNombre == null) {
                throw new ReglaException("Para menores de edad registre el acudiente o acompañante.");
            }
            if (nuevo) {
                if (!Form.si(f.get("consentimiento"))) {
                    throw new ReglaException("El paciente debe autorizar el tratamiento de datos personales (Ley 1581 de 2012).");
                }
                p.consentimientoDatos = LocalDateTime.now();
            }
            p = pacientes.guardar(p, nuevo);
        } catch (ReglaException e) {
            model.addAttribute("p", p);
            model.addAttribute("epsLista", eps.findByActivoTrueOrderByNombreAsc());
            model.addAttribute("mensajeError", e.getMessage());
            return "admisiones/paciente-form";
        }
        if (nuevo) {
            adres.consultar(p);
        }
        ra.addFlashAttribute("ok", nuevo ? "Paciente registrado con historia " + p.numeroHistoria + "." : "Datos actualizados.");
        return "redirect:/admisiones/paciente/" + p.id;
    }

    @GetMapping("/paciente/{id}")
    public String ficha(@PathVariable String id, Model model) {
        Paciente p = pacientes.obtener(id);
        auditoria.registrar("PACIENTE_CONSULTADO", "/admisiones/paciente", p, "Ficha administrativa");
        model.addAttribute("p", p);
        model.addAttribute("citas", citas.findByPacienteIdOrderByFechaHoraDesc(id));
        model.addAttribute("admisiones", admisiones.findByPacienteIdOrderByFechaIngresoDesc(id));
        return "admisiones/ficha";
    }

    @PostMapping("/paciente/{id}/derechos")
    public String derechos(@PathVariable String id, RedirectAttributes ra) {
        String estado = adres.consultar(pacientes.obtener(id));
        ra.addFlashAttribute("ok", "Consulta de derechos (simulación ADRES): " + estado);
        return "redirect:/admisiones/paciente/" + id;
    }

    @GetMapping("/citas")
    public String citas(@RequestParam(required = false) String agendaId, @RequestParam(required = false) String fecha,
                        @RequestParam(required = false) String pacienteId, Model model) {
        LocalDate dia = Form.fecha(fecha) == null ? LocalDate.now() : Form.fecha(fecha);
        List<Agenda> lista = agendas.findAllByOrderByProfesionalNombreAsc();
        Agenda sel = null;
        if (agendaId != null) {
            sel = agendaService.agenda(agendaId);
        } else if (!lista.isEmpty()) {
            sel = lista.get(0);
        }
        model.addAttribute("agendas", lista);
        model.addAttribute("agenda", sel);
        model.addAttribute("fecha", dia);
        model.addAttribute("franjas", sel == null ? List.of() : agendaService.franjasLibres(sel, dia));
        List<Cita> delDia = new ArrayList<>();
        if (sel != null) {
            delDia = citas.findByProfesionalIdAndFechaHoraBetween(sel.profesionalId, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
            delDia.sort((a, b) -> a.fechaHora.compareTo(b.fechaHora));
        }
        model.addAttribute("citasDia", delDia);
        model.addAttribute("paciente", pacienteId == null ? null : pacientes.obtener(pacienteId));
        return "admisiones/citas";
    }

    @PostMapping("/citas/asignar")
    public String asignar(@RequestParam String pacienteId, @RequestParam String agendaId, @RequestParam String fechaHora,
                          @RequestParam(required = false) String tipo, RedirectAttributes ra) {
        Cita c = agendaService.asignar(pacienteId, agendaId, Form.fechaHora(fechaHora), "VENTANILLA", Form.s(tipo));
        ra.addFlashAttribute("ok", "Cita asignada a " + c.pacienteNombre + " el " + c.fechaHora.toLocalDate() + " a las " + c.fechaHora.toLocalTime() + ".");
        return "redirect:/admisiones/citas?agendaId=" + agendaId + "&fecha=" + c.fechaHora.toLocalDate();
    }

    @PostMapping("/citas/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String motivo, RedirectAttributes ra) {
        Cita c = agendaService.cita(id);
        agendaService.cancelar(id, Form.s(motivo), null);
        ra.addFlashAttribute("ok", "Cita cancelada y franja liberada.");
        return "redirect:/admisiones/citas?fecha=" + c.fechaHora.toLocalDate();
    }

    @GetMapping("/urgencias")
    public String urgencias(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("resultados", Form.s(q) == null ? List.of() : pacientes.buscar(q, 10));
        model.addAttribute("q", q);
        List<Admision> activas = admisiones.findByTipoAndEstadoInOrderByFechaIngresoAsc("URGENCIAS", List.of("EN_ESPERA_TRIAJE", "TRIADO", "EN_ATENCION"));
        model.addAttribute("activas", activas);
        return "admisiones/urgencias";
    }

    private Admision ingresoUrgencias(Paciente p, String motivo) {
        if (motivo == null) {
            throw new ReglaException("Escriba el motivo de consulta referido por el paciente.");
        }
        for (Admision a : admisiones.findByPacienteIdOrderByFechaIngresoDesc(p.id)) {
            if ("URGENCIAS".equals(a.tipo) && !"ATENDIDO".equals(a.estado)) {
                throw new ReglaException("El paciente ya tiene un ingreso activo en urgencias.");
            }
        }
        Admision a = new Admision();
        a.pacienteId = p.id;
        a.pacienteNombre = p.nombreCompleto();
        a.pacienteDocumento = p.documentoTexto();
        a.pacienteSexo = p.sexo;
        a.tipo = "URGENCIAS";
        a.estado = "EN_ESPERA_TRIAJE";
        a.motivo = motivo;
        a.epsNombre = p.epsNombre;
        a.derechosEstado = p.derechosEstado;
        a.creadoPor = sesion.nombre();
        a = admisiones.save(a);
        auditoria.registrar("INGRESO_URGENCIAS", "/admisiones/urgencias", p, motivo);
        return a;
    }

    @PostMapping("/urgencias/ingresar")
    public String ingresar(@RequestParam String pacienteId, @RequestParam(required = false) String motivo, RedirectAttributes ra) {
        Paciente p = pacientes.obtener(pacienteId);
        String estado = p.noIdentificado ? "NO VERIFICABLE" : adres.consultar(p);
        ingresoUrgencias(p, Form.s(motivo));
        String msg = p.nombreCompleto() + " ingresó a la cola de triaje.";
        if (!"ACTIVO".equals(estado)) {
            msg += " Atención: derechos " + estado + ". La urgencia vital se atiende igual (Ley 1751 de 2015); informe a facturación.";
        }
        ra.addFlashAttribute("ok", msg);
        return "redirect:/admisiones/urgencias";
    }

    @PostMapping("/urgencias/nn")
    public String ingresarNN(@RequestParam(required = false) String sexo, @RequestParam(required = false) String edad,
                             @RequestParam(required = false) String descripcion, @RequestParam(required = false) String motivo, RedirectAttributes ra) {
        if (Form.s(motivo) == null) {
            throw new ReglaException("Escriba el motivo de ingreso.");
        }
        Paciente p = pacientes.crearNoIdentificado(Form.s(sexo), Form.s(descripcion), Form.i(edad));
        ingresoUrgencias(p, Form.s(motivo));
        ra.addFlashAttribute("ok", "Paciente no identificado registrado como " + p.numeroDocumento + " (" + p.numeroHistoria
            + "). Actualice sus datos cuando sea identificado.");
        return "redirect:/admisiones/urgencias";
    }
}
