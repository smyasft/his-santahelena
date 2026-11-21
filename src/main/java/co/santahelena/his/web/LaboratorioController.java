package co.santahelena.his.web;

import java.util.Comparator;
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

import co.santahelena.his.model.ExamenLab;
import co.santahelena.his.model.OrdenLab;
import co.santahelena.his.repo.ExamenLabRepo;
import co.santahelena.his.repo.OrdenLabRepo;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.LabService;
import co.santahelena.his.service.PacienteService;
import co.santahelena.his.util.Code39;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.ReglaException;

@Controller
@RequestMapping("/laboratorio")
public class LaboratorioController {
    private final LabService lab;
    private final OrdenLabRepo ordenes;
    private final ExamenLabRepo examenes;
    private final PacienteService pacientes;
    private final AuditoriaService auditoria;

    public LaboratorioController(LabService lab, OrdenLabRepo ordenes, ExamenLabRepo examenes, PacienteService pacientes, AuditoriaService auditoria) {
        this.lab = lab;
        this.ordenes = ordenes;
        this.examenes = examenes;
        this.pacientes = pacientes;
        this.auditoria = auditoria;
    }

    @GetMapping
    public String ordenes(Model model) {
        List<OrdenLab> lista = ordenes.findByEstadoInOrderByFechaAsc(List.of("ORDENADA", "RECIBIDA", "RESULTADOS"));
        lista.sort(Comparator.comparing((OrdenLab o) -> "URGENTE".equals(o.prioridad) ? 0 : 1).thenComparing(o -> o.fecha));
        model.addAttribute("lista", lista);
        return "laboratorio/ordenes";
    }

    @GetMapping("/validados")
    public String validados(Model model) {
        List<OrdenLab> v = ordenes.findByEstadoOrderByValidadaEnDesc("VALIDADA");
        model.addAttribute("lista", v.size() > 100 ? v.subList(0, 100) : v);
        return "laboratorio/validados";
    }

    @PostMapping("/orden/{id}/recibir")
    public String recibir(@PathVariable String id, RedirectAttributes ra) {
        OrdenLab o = lab.recibir(id);
        ra.addFlashAttribute("ok", "Muestra recibida con código " + o.codigoMuestra + ".");
        return "redirect:/laboratorio/orden/" + id + "/etiqueta";
    }

    @GetMapping("/orden/{id}/etiqueta")
    public String etiqueta(@PathVariable String id, Model model) {
        OrdenLab o = lab.orden(id);
        if (o.codigoMuestra == null) {
            throw new ReglaException("La muestra aún no se ha recibido.");
        }
        model.addAttribute("o", o);
        model.addAttribute("barras", Code39.svg(o.codigoMuestra, 46));
        return "imprimir/etiqueta";
    }

    @PostMapping("/orden/{id}/rechazar")
    public String rechazar(@PathVariable String id, @RequestParam(required = false) String motivo, RedirectAttributes ra) {
        lab.rechazar(id, Form.s(motivo));
        ra.addFlashAttribute("ok", "Muestra rechazada; se notificó al médico.");
        return "redirect:/laboratorio";
    }

    @GetMapping("/orden/{id}")
    public String orden(@PathVariable String id, Model model) {
        OrdenLab o = lab.orden(id);
        model.addAttribute("o", o);
        model.addAttribute("p", pacientes.obtener(o.pacienteId));
        java.util.List<ExamenLab> cat = new java.util.ArrayList<>();
        for (OrdenLab.ExamenOrden e : o.examenes) {
            cat.add(examenes.findByCodigo(e.codigo).orElseThrow());
        }
        model.addAttribute("catalogo", cat);
        return "laboratorio/orden";
    }

    @PostMapping("/orden/{id}/resultados")
    public String resultados(@PathVariable String id, @RequestParam Map<String, String> f, RedirectAttributes ra) {
        lab.registrarResultados(id, f);
        ra.addFlashAttribute("ok", "Resultados guardados. Revise y valide para publicarlos.");
        return "redirect:/laboratorio/orden/" + id;
    }

    @PostMapping("/orden/{id}/validar")
    public String validar(@PathVariable String id, RedirectAttributes ra) {
        lab.validar(id);
        OrdenLab o = lab.orden(id);
        ra.addFlashAttribute(o.tieneCriticos() ? "error" : "ok", o.tieneCriticos()
            ? "Resultados validados con VALORES CRÍTICOS: se alertó al médico y a enfermería."
            : "Resultados validados y publicados en la historia y el portal del paciente.");
        return "redirect:/laboratorio";
    }

    @GetMapping("/orden/{id}/imprimir")
    public String imprimir(@PathVariable String id, Model model) {
        OrdenLab o = lab.orden(id);
        model.addAttribute("o", o);
        model.addAttribute("p", pacientes.obtener(o.pacienteId));
        return "imprimir/resultado";
    }

    @GetMapping("/examenes")
    public String examenes(Model model) {
        model.addAttribute("lista", examenes.findAllByOrderByNombreAsc());
        return "laboratorio/examenes";
    }

    @GetMapping("/examenes/nuevo")
    public String nuevoExamen(Model model) {
        model.addAttribute("e", new ExamenLab());
        model.addAttribute("filas", List.of(0, 1, 2, 3, 4, 5));
        return "laboratorio/examen-form";
    }

    @GetMapping("/examenes/{id}")
    public String editarExamen(@PathVariable String id, Model model) {
        model.addAttribute("e", examenes.findById(id).orElseThrow(() -> new ReglaException("Examen no encontrado.")));
        model.addAttribute("filas", List.of(0, 1, 2, 3, 4, 5));
        return "laboratorio/examen-form";
    }

    @PostMapping("/examenes/guardar")
    public String guardarExamen(@RequestParam MultiValueMap<String, String> f, RedirectAttributes ra) {
        String id = F.v(f, "id");
        ExamenLab e = id == null ? new ExamenLab() : examenes.findById(id).orElseThrow();
        e.codigo = F.v(f, "codigo");
        e.nombre = F.v(f, "nombre");
        e.muestra = F.v(f, "muestra");
        Double precio = Form.d(F.v(f, "precio"));
        e.precio = precio == null ? 0 : precio;
        e.activo = Form.si(F.v(f, "activo"));
        if (e.codigo == null || e.nombre == null) {
            throw new ReglaException("Código CUPS y nombre son obligatorios.");
        }
        ExamenLab otro = examenes.findByCodigo(e.codigo).orElse(null);
        if (otro != null && !otro.id.equals(e.id)) {
            throw new ReglaException("Ya existe un examen con el código " + e.codigo + ".");
        }
        e.analitos.clear();
        List<String> n = F.l(f, "an_nombre");
        for (int i = 0; i < n.size(); i++) {
            String nombre = F.at(n, i);
            if (nombre == null) {
                continue;
            }
            ExamenLab.Analito a = new ExamenLab.Analito();
            a.nombre = nombre;
            a.unidad = F.at(F.l(f, "an_unidad"), i);
            a.min = Form.d(F.at(F.l(f, "an_min"), i));
            a.max = Form.d(F.at(F.l(f, "an_max"), i));
            a.criticoMin = Form.d(F.at(F.l(f, "an_cmin"), i));
            a.criticoMax = Form.d(F.at(F.l(f, "an_cmax"), i));
            if (a.min != null && a.max != null && a.min > a.max) {
                throw new ReglaException(nombre + ": el mínimo de referencia es mayor que el máximo.");
            }
            e.analitos.add(a);
        }
        if (e.analitos.isEmpty()) {
            throw new ReglaException("Agregue al menos un analito.");
        }
        examenes.save(e);
        auditoria.registrar("EXAMEN_CATALOGO", "/laboratorio/examenes", null, null, e.codigo + " " + e.nombre);
        ra.addFlashAttribute("ok", "Examen guardado.");
        return "redirect:/laboratorio/examenes";
    }
}
