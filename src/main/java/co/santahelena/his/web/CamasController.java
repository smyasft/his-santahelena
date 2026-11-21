package co.santahelena.his.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import co.santahelena.his.model.Cama;
import co.santahelena.his.repo.AdmisionRepo;
import co.santahelena.his.repo.CamaRepo;
import co.santahelena.his.repo.PazYSalvoRepo;
import co.santahelena.his.service.CamaService;
import co.santahelena.his.service.PacienteService;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.Reglas;

@Controller
@RequestMapping("/camas")
public class CamasController {
    private final CamaService camaService;
    private final CamaRepo camas;
    private final AdmisionRepo admisiones;
    private final PazYSalvoRepo pazYSalvos;
    private final PacienteService pacientes;

    public CamasController(CamaService camaService, CamaRepo camas, AdmisionRepo admisiones, PazYSalvoRepo pazYSalvos, PacienteService pacientes) {
        this.camaService = camaService;
        this.camas = camas;
        this.admisiones = admisiones;
        this.pazYSalvos = pazYSalvos;
        this.pacientes = pacientes;
    }

    @GetMapping
    public String mapa(Model model) {
        Map<String, List<Cama>> porServicio = new LinkedHashMap<>();
        int libres = 0;
        int ocupadas = 0;
        int otras = 0;
        for (Cama c : camas.findAllByOrderByServicioAscCodigoAsc()) {
            porServicio.computeIfAbsent(c.servicio, k -> new ArrayList<>()).add(c);
            if ("LIBRE".equals(c.estado)) {
                libres++;
            } else if ("OCUPADA".equals(c.estado)) {
                ocupadas++;
            } else {
                otras++;
            }
        }
        Map<String, Admision> admPorCama = new LinkedHashMap<>();
        for (Admision a : admisiones.findByEstadoOrderByFechaIngresoAsc("EN_CAMA")) {
            if (a.camaId != null) {
                admPorCama.put(a.camaId, a);
            }
        }
        model.addAttribute("servicios", porServicio);
        model.addAttribute("admPorCama", admPorCama);
        model.addAttribute("libres", libres);
        model.addAttribute("ocupadas", ocupadas);
        model.addAttribute("otras", otras);
        int total = libres + ocupadas + otras;
        model.addAttribute("ocupacion", total == 0 ? 0 : ocupadas * 100 / total);
        model.addAttribute("pendientes", admisiones.findByEstadoOrderByFechaIngresoAsc("PENDIENTE_CAMA").size());
        return "camas/mapa";
    }

    public static class Opcion {
        public Cama cama;
        public String problema;
    }

    private List<Opcion> opciones(Admision a, boolean mismoServicio) {
        List<Opcion> r = new ArrayList<>();
        for (Cama c : camas.findByEstado("LIBRE")) {
            Opcion o = new Opcion();
            o.cama = c;
            o.problema = Reglas.incompatibilidadCama(c, a.pacienteSexo, a.requiereAislamiento, mismoServicio ? a.servicio : c.servicio);
            r.add(o);
        }
        r.sort((x, y) -> {
            if ((x.problema == null) != (y.problema == null)) {
                return x.problema == null ? -1 : 1;
            }
            return x.cama.codigo.compareTo(y.cama.codigo);
        });
        return r;
    }

    @GetMapping("/pendientes")
    public String pendientes(Model model) {
        List<Admision> lista = admisiones.findByEstadoOrderByFechaIngresoAsc("PENDIENTE_CAMA");
        Map<String, List<Opcion>> ops = new LinkedHashMap<>();
        for (Admision a : lista) {
            ops.put(a.id, opciones(a, true));
        }
        model.addAttribute("lista", lista);
        model.addAttribute("opciones", ops);
        return "camas/pendientes";
    }

    @PostMapping("/asignar")
    public String asignar(@RequestParam String admisionId, @RequestParam String camaId, RedirectAttributes ra) {
        camaService.asignar(admisionId, camaId);
        Cama c = camaService.cama(camaId);
        ra.addFlashAttribute("ok", "Cama " + c.codigo + " asignada a " + c.pacienteNombre + ".");
        return "redirect:/camas";
    }

    @GetMapping("/admision/{id}")
    public String admision(@PathVariable String id, Model model) {
        Admision a = camaService.admision(id);
        model.addAttribute("a", a);
        model.addAttribute("p", pacientes.obtener(a.pacienteId));
        model.addAttribute("dias", camaService.diasEstancia(a));
        model.addAttribute("pys", pazYSalvos.findFirstByAdmisionId(a.id).orElse(null));
        model.addAttribute("opciones", opciones(a, false));
        return "camas/admision";
    }

    @PostMapping("/admision/{id}/traslado")
    public String traslado(@PathVariable String id, @RequestParam String camaId, @RequestParam(required = false) String motivo, RedirectAttributes ra) {
        camaService.trasladar(id, camaId, Form.s(motivo));
        ra.addFlashAttribute("ok", "Traslado registrado. La cama anterior quedó en limpieza.");
        return "redirect:/camas/admision/" + id;
    }

    @PostMapping("/admision/{id}/liquidar")
    public String liquidar(@PathVariable String id, RedirectAttributes ra) {
        camaService.liquidarEstancia(id);
        ra.addFlashAttribute("ok", "Estancia liquidada y cargada a la cuenta. Facturación ya puede expedir el paz y salvo.");
        return "redirect:/camas/admision/" + id;
    }

    @PostMapping("/admision/{id}/egresar")
    public String egresar(@PathVariable String id, RedirectAttributes ra) {
        Admision a = camaService.admision(id);
        camaService.egresar(id);
        ra.addFlashAttribute("ok", a.pacienteNombre + " egresó. La cama " + a.camaCodigo + " pasó a limpieza.");
        return "redirect:/camas";
    }

    @PostMapping("/cama/{id}/estado")
    public String estado(@PathVariable String id, @RequestParam String estado, @RequestParam(required = false) String motivo, RedirectAttributes ra) {
        camaService.cambiarEstado(id, estado, Form.s(motivo));
        ra.addFlashAttribute("ok", "Estado de la cama actualizado.");
        return "redirect:/camas";
    }
}
