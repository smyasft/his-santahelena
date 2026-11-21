package co.santahelena.his.web;

import java.time.LocalDateTime;
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

import co.santahelena.his.model.Atencion;
import co.santahelena.his.model.CatalogoItem;
import co.santahelena.his.model.Cirugia;
import co.santahelena.his.model.Consentimiento;
import co.santahelena.his.model.Interconsulta;
import co.santahelena.his.model.Paciente;
import co.santahelena.his.model.Usuario;
import co.santahelena.his.repo.CatalogoRepo;
import co.santahelena.his.repo.CirugiaRepo;
import co.santahelena.his.repo.ConsentimientoRepo;
import co.santahelena.his.repo.InterconsultaRepo;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.HceService;
import co.santahelena.his.service.PacienteService;
import co.santahelena.his.service.SesionService;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.Hash;
import co.santahelena.his.util.ReglaException;

@Controller
@RequestMapping("/especialista")
public class EspecialistaController {
    public static final List<String> QUIROFANOS = List.of("Quirófano 1", "Quirófano 2", "Quirófano 3");

    private final SesionService sesion;
    private final PacienteService pacientes;
    private final HceService hce;
    private final InterconsultaRepo interconsultas;
    private final ConsentimientoRepo consentimientos;
    private final CirugiaRepo cirugias;
    private final CatalogoRepo catalogos;
    private final AuditoriaService auditoria;

    public EspecialistaController(SesionService sesion, PacienteService pacientes, HceService hce, InterconsultaRepo interconsultas,
                                  ConsentimientoRepo consentimientos, CirugiaRepo cirugias, CatalogoRepo catalogos, AuditoriaService auditoria) {
        this.sesion = sesion;
        this.pacientes = pacientes;
        this.hce = hce;
        this.interconsultas = interconsultas;
        this.consentimientos = consentimientos;
        this.cirugias = cirugias;
        this.catalogos = catalogos;
        this.auditoria = auditoria;
    }

    @GetMapping
    public String interconsultas(Model model) {
        model.addAttribute("lista", interconsultas.findByEstadoInOrderByFechaAsc(List.of("SOLICITADA", "ACEPTADA")));
        model.addAttribute("respondidas", interconsultas.findByEstadoInOrderByFechaAsc(List.of("RESPONDIDA")));
        return "especialista/interconsultas";
    }

    @PostMapping("/interconsulta/{id}/aceptar")
    public String aceptar(@PathVariable String id, RedirectAttributes ra) {
        Interconsulta i = interconsultas.findById(id).orElseThrow(() -> new ReglaException("Interconsulta no encontrada."));
        if (!"SOLICITADA".equals(i.estado)) {
            throw new ReglaException("La interconsulta ya fue tomada por " + i.especialistaNombre + ".");
        }
        Usuario u = sesion.requerido();
        i.estado = "ACEPTADA";
        i.especialistaId = u.id;
        i.especialistaNombre = u.nombre;
        i.aceptadaEn = LocalDateTime.now();
        interconsultas.save(i);
        auditoria.registrar("INTERCONSULTA_ACEPTADA", "/especialista", i.pacienteId, i.pacienteNombre, i.especialidad);
        ra.addFlashAttribute("ok", "Interconsulta aceptada.");
        return "redirect:/especialista";
    }

    @PostMapping("/interconsulta/{id}/atender")
    public String atender(@PathVariable String id) {
        Interconsulta i = interconsultas.findById(id).orElseThrow(() -> new ReglaException("Interconsulta no encontrada."));
        Usuario u = sesion.requerido();
        if (!"ACEPTADA".equals(i.estado) || !u.id.equals(i.especialistaId)) {
            throw new ReglaException("Primero acepte la interconsulta.");
        }
        Atencion a = hce.nueva(pacientes.obtener(i.pacienteId), "ESPECIALIDAD", null, null, i.id);
        a.motivoConsulta = "Interconsulta: " + i.motivo;
        hce.guardar(a);
        return "redirect:/hce/atencion/" + a.id;
    }

    @GetMapping("/consentimientos")
    public String consentimientos(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("lista", consentimientos.findAllByOrderByFechaDesc());
        model.addAttribute("resultados", Form.s(q) == null ? List.of() : pacientes.buscar(q, 10));
        model.addAttribute("q", q);
        return "especialista/consentimientos";
    }

    @GetMapping("/consentimientos/nuevo")
    public String nuevoConsentimiento(@RequestParam String pacienteId, Model model) {
        model.addAttribute("p", pacientes.obtener(pacienteId));
        return "especialista/consentimiento-form";
    }

    @PostMapping("/consentimientos")
    public String guardarConsentimiento(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        Paciente p = pacientes.obtener(f.get("pacienteId"));
        Usuario u = sesion.requerido();
        Consentimiento c = new Consentimiento();
        c.pacienteId = p.id;
        c.pacienteNombre = p.nombreCompleto();
        c.pacienteDocumento = p.documentoTexto();
        c.procedimiento = Form.s(f.get("procedimiento"));
        c.descripcion = Form.s(f.get("descripcion"));
        c.riesgos = Form.s(f.get("riesgos"));
        c.alternativas = Form.s(f.get("alternativas"));
        c.acepta = "SI".equals(f.get("decision"));
        c.firmanteNombre = Form.s(f.get("firmanteNombre"));
        c.firmanteDocumento = Form.s(f.get("firmanteDocumento"));
        c.parentesco = Form.s(f.get("parentesco"));
        c.testigo = Form.s(f.get("testigo"));
        c.especialistaNombre = u.nombre;
        c.especialistaRegistro = u.registroMedico;
        if (c.procedimiento == null || c.riesgos == null || c.firmanteNombre == null || c.firmanteDocumento == null || f.get("decision") == null) {
            throw new ReglaException("Procedimiento, riesgos, decisión y datos del firmante son obligatorios.");
        }
        if (p.edad() < 18 && (c.parentesco == null || "Paciente".equals(c.parentesco))) {
            throw new ReglaException("El paciente es menor de edad: debe firmar su representante legal.");
        }
        if (!Form.si(f.get("confirmaLectura"))) {
            throw new ReglaException("Confirme que el paciente o su representante recibió la información y resolvió sus dudas.");
        }
        c.hash = Hash.sha256(c.pacienteId + c.procedimiento + c.firmanteDocumento + LocalDateTime.now());
        c = consentimientos.save(c);
        auditoria.registrar("CONSENTIMIENTO", "/especialista", p, c.procedimiento + (c.acepta ? " – ACEPTA" : " – NO ACEPTA"));
        ra.addFlashAttribute("ok", "Consentimiento registrado.");
        return "redirect:/especialista/consentimientos/" + c.id + "/imprimir";
    }

    @GetMapping("/consentimientos/{id}/imprimir")
    public String imprimirConsentimiento(@PathVariable String id, Model model) {
        Consentimiento c = consentimientos.findById(id).orElseThrow(() -> new ReglaException("Consentimiento no encontrado."));
        model.addAttribute("c", c);
        model.addAttribute("p", pacientes.obtener(c.pacienteId));
        return "imprimir/consentimiento";
    }

    @GetMapping("/cirugias")
    public String cirugias(@RequestParam(required = false) String q, @RequestParam(required = false) String pacienteId, Model model) {
        model.addAttribute("lista", cirugias.findAllByOrderByInicioAsc());
        model.addAttribute("quirofanos", QUIROFANOS);
        model.addAttribute("resultados", Form.s(q) == null ? List.of() : pacientes.buscar(q, 10));
        model.addAttribute("q", q);
        Paciente p = Form.s(pacienteId) == null ? null : pacientes.obtener(pacienteId);
        model.addAttribute("p", p);
        model.addAttribute("consentimientosPaciente", p == null ? List.of() : consentimientos.findByPacienteIdOrderByFechaDesc(p.id).stream().filter(c -> c.acepta).toList());
        return "especialista/cirugias";
    }

    /** Programación quirúrgica: exige consentimiento aceptado y evita cruces en el mismo quirófano (US-039/040). */
    @PostMapping("/cirugias")
    public String programar(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        Paciente p = pacientes.obtener(f.get("pacienteId"));
        String codigo = Form.s(f.get("procedimientoCodigo"));
        LocalDateTime inicio = Form.fechaHora(f.get("inicio"));
        Integer dur = Form.i(f.get("duracion"));
        String quirofano = Form.s(f.get("quirofano"));
        String consentimientoId = Form.s(f.get("consentimientoId"));
        if (codigo == null || inicio == null || dur == null || dur <= 0 || quirofano == null) {
            throw new ReglaException("Procedimiento, quirófano, fecha/hora y duración son obligatorios.");
        }
        if (inicio.isBefore(LocalDateTime.now())) {
            throw new ReglaException("No se puede programar en una fecha pasada.");
        }
        if (consentimientoId == null) {
            throw new ReglaException("Seleccione el consentimiento informado firmado (aceptado) del paciente.");
        }
        CatalogoItem proc = catalogos.findByTipoAndCodigo("CUPS", codigo.split(" ")[0])
            .orElseThrow(() -> new ReglaException("El código CUPS no existe en el catálogo."));
        LocalDateTime fin = inicio.plusMinutes(dur);
        for (Cirugia c : cirugias.findByQuirofanoAndEstado(quirofano, "PROGRAMADA")) {
            if (inicio.isBefore(c.fin) && fin.isAfter(c.inicio)) {
                throw new ReglaException(quirofano + " está ocupado de " + c.inicio.toLocalTime() + " a " + c.fin.toLocalTime()
                    + " (" + c.procedimientoNombre + "). Elija otro horario o quirófano.");
            }
        }
        Usuario u = sesion.requerido();
        Cirugia c = new Cirugia();
        c.pacienteId = p.id;
        c.pacienteNombre = p.nombreCompleto();
        c.pacienteDocumento = p.documentoTexto();
        c.procedimientoCodigo = proc.codigo;
        c.procedimientoNombre = proc.descripcion;
        c.quirofano = quirofano;
        c.inicio = inicio;
        c.fin = fin;
        c.duracionMinutos = dur;
        c.anestesia = Form.s(f.get("anestesia"));
        c.especialistaNombre = u.nombre;
        c.consentimientoId = consentimientoId;
        c.programadaPor = u.nombre;
        cirugias.save(c);
        auditoria.registrar("CIRUGIA_PROGRAMADA", "/especialista/cirugias", p, proc.descripcion + " " + quirofano + " " + inicio);
        ra.addFlashAttribute("ok", "Cirugía programada en " + quirofano + ".");
        return "redirect:/especialista/cirugias";
    }

    @PostMapping("/cirugias/{id}/estado")
    public String estadoCirugia(@PathVariable String id, @RequestParam String estado, RedirectAttributes ra) {
        Cirugia c = cirugias.findById(id).orElseThrow(() -> new ReglaException("Cirugía no encontrada."));
        if (!"PROGRAMADA".equals(c.estado)) {
            throw new ReglaException("La cirugía ya está " + c.estado.toLowerCase() + ".");
        }
        if (!"REALIZADA".equals(estado) && !"CANCELADA".equals(estado)) {
            throw new ReglaException("Estado no válido.");
        }
        c.estado = estado;
        cirugias.save(c);
        auditoria.registrar("CIRUGIA_" + estado, "/especialista/cirugias", c.pacienteId, c.pacienteNombre, c.procedimientoNombre);
        ra.addFlashAttribute("ok", "Cirugía marcada como " + estado.toLowerCase() + ".");
        return "redirect:/especialista/cirugias";
    }
}
