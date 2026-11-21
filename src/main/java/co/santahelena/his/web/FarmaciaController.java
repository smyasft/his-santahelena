package co.santahelena.his.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

import co.santahelena.his.model.Lote;
import co.santahelena.his.model.Medicamento;
import co.santahelena.his.model.Prescripcion;
import co.santahelena.his.repo.LoteRepo;
import co.santahelena.his.repo.MedicamentoRepo;
import co.santahelena.his.repo.MovimientoRepo;
import co.santahelena.his.repo.PrescripcionRepo;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.CumService;
import co.santahelena.his.service.FarmaciaService;
import co.santahelena.his.service.PacienteService;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.ReglaException;

@Controller
@RequestMapping("/farmacia")
public class FarmaciaController {
    private final FarmaciaService farmacia;
    private final PrescripcionRepo prescripciones;
    private final MedicamentoRepo medicamentos;
    private final LoteRepo lotes;
    private final MovimientoRepo movimientos;
    private final PacienteService pacientes;
    private final CumService cum;
    private final AuditoriaService auditoria;

    public FarmaciaController(FarmaciaService farmacia, PrescripcionRepo prescripciones, MedicamentoRepo medicamentos, LoteRepo lotes,
                              MovimientoRepo movimientos, PacienteService pacientes, CumService cum, AuditoriaService auditoria) {
        this.cum = cum;
        this.auditoria = auditoria;
        this.farmacia = farmacia;
        this.prescripciones = prescripciones;
        this.medicamentos = medicamentos;
        this.lotes = lotes;
        this.movimientos = movimientos;
        this.pacientes = pacientes;
    }

    @GetMapping
    public String pendientes(Model model) {
        List<Prescripcion> lista = prescripciones.findByEstadoOrderByFechaDesc("ACTIVA");
        lista.sort(Comparator.comparing((Prescripcion p) -> "PENDIENTE".equals(p.validacionFarmacia) ? 0 : 1).thenComparing(p -> p.fecha));
        model.addAttribute("lista", lista);
        return "farmacia/pendientes";
    }

    @GetMapping("/prescripcion/{id}")
    public String prescripcion(@PathVariable String id, Model model) {
        Prescripcion pr = farmacia.prescripcion(id);
        Map<String, Integer> stock = new HashMap<>();
        for (Prescripcion.Item it : pr.items) {
            stock.put(it.medicamentoId, farmacia.stock(it.medicamentoId));
        }
        model.addAttribute("pr", pr);
        model.addAttribute("p", pacientes.obtener(pr.pacienteId));
        model.addAttribute("stock", stock);
        return "farmacia/prescripcion";
    }

    @PostMapping("/prescripcion/{id}/validar")
    public String validar(@PathVariable String id, @RequestParam String decision, @RequestParam(required = false) String observacion, RedirectAttributes ra) {
        farmacia.validar(id, "VALIDADA".equals(decision), Form.s(observacion));
        ra.addFlashAttribute("ok", "VALIDADA".equals(decision) ? "Fórmula validada: puede dispensar." : "Fórmula observada; el médico verá la observación.");
        return "redirect:/farmacia/prescripcion/" + id;
    }

    @PostMapping("/prescripcion/{id}/dispensar")
    public String dispensar(@PathVariable String id, @RequestParam Map<String, String> f, RedirectAttributes ra) {
        Map<Integer, Integer> cant = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : f.entrySet()) {
            if (e.getKey().startsWith("q_")) {
                Integer q = Form.i(e.getValue());
                cant.put(Integer.parseInt(e.getKey().substring(2)), q == null ? 0 : q);
            }
        }
        farmacia.dispensar(id, cant);
        ra.addFlashAttribute("ok", "Dispensación registrada (descuento por lote FEFO y cargo a la cuenta del paciente).");
        return "redirect:/farmacia/prescripcion/" + id;
    }

    public static class FilaInventario {
        public Medicamento m;
        public int stock;
        public int vencidos;
        public boolean porVencer;
        public List<Lote> lotes = new ArrayList<>();
    }

    @GetMapping("/inventario")
    public String inventario(@RequestParam(required = false) String q, @RequestParam(required = false) String filtro, Model model) {
        List<FilaInventario> filas = new ArrayList<>();
        String n = Form.norm(q);
        LocalDate hoy = LocalDate.now();
        for (Medicamento m : medicamentos.findAllByOrderByNombreAsc()) {
            if (!n.isEmpty() && !Form.norm(m.nombre + " " + m.codigoCum).contains(n)) {
                continue;
            }
            FilaInventario fi = new FilaInventario();
            fi.m = m;
            fi.lotes = lotes.findByMedicamentoId(m.id);
            fi.lotes.sort(Comparator.comparing(l -> l.vence));
            for (Lote l : fi.lotes) {
                if (l.vence.isBefore(hoy)) {
                    fi.vencidos += l.cantidad;
                } else {
                    fi.stock += l.cantidad;
                    if (l.porVencer() && l.cantidad > 0) {
                        fi.porVencer = true;
                    }
                }
            }
            if ("bajo".equals(filtro) && fi.stock >= m.stockMinimo) {
                continue;
            }
            if ("vencer".equals(filtro) && !fi.porVencer && fi.vencidos == 0) {
                continue;
            }
            filas.add(fi);
        }
        model.addAttribute("filas", filas);
        model.addAttribute("q", q);
        model.addAttribute("filtro", filtro);
        model.addAttribute("medicamentos", medicamentos.findAllByOrderByNombreAsc());
        return "farmacia/inventario";
    }

    @PostMapping("/lotes")
    public String ingresarLote(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        Integer cant = Form.i(f.get("cantidad"));
        farmacia.ingresarLote(f.get("medicamentoId"), Form.s(f.get("numero")), Form.fecha(f.get("vence")), cant == null ? 0 : cant, Form.s(f.get("proveedor")));
        ra.addFlashAttribute("ok", "Lote ingresado al inventario.");
        return "redirect:/farmacia/inventario";
    }

    @GetMapping("/kardex/{medicamentoId}")
    public String kardex(@PathVariable String medicamentoId, Model model) {
        Medicamento m = medicamentos.findById(medicamentoId).orElseThrow(() -> new ReglaException("Medicamento no encontrado."));
        model.addAttribute("m", m);
        model.addAttribute("movimientos", movimientos.findByMedicamentoIdOrderByFechaDesc(medicamentoId));
        model.addAttribute("stock", farmacia.stock(medicamentoId));
        return "farmacia/kardex";
    }

    @GetMapping("/control")
    public String control(Model model) {
        model.addAttribute("movimientos", movimientos.findByControlEspecialTrueOrderByFechaDesc());
        return "farmacia/control";
    }

    // ------------------------------------------------------------------ catálogo oficial INVIMA (datos.gov.co)

    @GetMapping("/invima")
    public String invima(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("grupos", CumService.gruposDisponibles());
        List<CumService.Resultado> resultados = new ArrayList<>();
        if (Form.s(q) != null) {
            try {
                resultados = cum.buscar(q);
                for (CumService.Resultado r : resultados) {
                    r.enInventario = medicamentos.findByCodigoCum(r.cum).isPresent();
                }
                auditoria.registrar("CONSULTA_INVIMA", "/farmacia/invima", null, null, q + " – " + resultados.size() + " resultado(s)");
            } catch (ReglaException e) {
                model.addAttribute("mensajeError", e.getMessage());
            }
        }
        model.addAttribute("resultados", resultados);
        model.addAttribute("buscado", Form.s(q) != null);
        return "farmacia/invima";
    }

    /** Importa un CUM al inventario: se vuelve a consultar al INVIMA para no confiar en los datos del formulario. */
    @PostMapping("/invima/importar")
    public String importar(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        CumService.Resultado r = cum.porCum(Form.s(f.get("expediente")), Form.s(f.get("consecutivo")));
        if (medicamentos.findByCodigoCum(r.cum).isPresent()) {
            throw new ReglaException("El CUM " + r.cum + " ya está en el inventario.");
        }
        String nombre = Form.s(f.get("nombre"));
        Integer minimo = Form.i(f.get("stockMinimo"));
        Double precio = Form.d(f.get("precio"));
        if (nombre == null) {
            throw new ReglaException("Escriba el nombre con el que se verá el medicamento en el HIS.");
        }
        if (minimo == null || minimo < 0 || precio == null || precio < 0) {
            throw new ReglaException("Indique el stock mínimo y el precio de venta.");
        }
        String grupo = Form.s(f.get("grupo"));
        if (grupo == null || !CumService.gruposDisponibles().contains(grupo)) {
            grupo = r.grupoSugerido;
        }
        Medicamento m = new Medicamento();
        m.codigoCum = r.cum;
        m.nombre = nombre;
        m.formaFarmaceutica = r.formaFarmaceutica;
        m.viaPredeterminada = r.via == null ? null : r.via.substring(0, 1) + r.via.substring(1).toLowerCase();
        m.grupo = grupo;
        m.controlEspecial = Form.si(f.get("controlEspecial"));
        m.stockMinimo = minimo;
        m.precio = precio;
        m.principioActivo = r.principiosTexto();
        m.titular = r.titular;
        m.registroSanitario = r.registroSanitario;
        m.atc = r.atc;
        m.descripcionAtc = r.descripcionAtc;
        m.presentacion = r.presentacion;
        m.fuente = "INVIMA · datos.gov.co";
        m.importadoEn = java.time.LocalDateTime.now();
        medicamentos.save(m);
        auditoria.registrar("MEDICAMENTO_IMPORTADO", "/farmacia/invima", null, null, r.cum + " " + nombre + " (" + r.registroSanitario + ")");
        ra.addFlashAttribute("ok", nombre + " (CUM " + r.cum + ") se agregó al inventario. Ahora ingrese su primer lote.");
        return "redirect:/farmacia/inventario?q=" + java.net.URLEncoder.encode(r.cum, java.nio.charset.StandardCharsets.UTF_8);
    }
}
