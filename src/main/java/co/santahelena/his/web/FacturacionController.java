package co.santahelena.his.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import co.santahelena.his.model.Admision;
import co.santahelena.his.model.Cuenta;
import co.santahelena.his.model.Eps;
import co.santahelena.his.model.Factura;
import co.santahelena.his.model.PazYSalvo;
import co.santahelena.his.repo.AdmisionRepo;
import co.santahelena.his.repo.CuentaRepo;
import co.santahelena.his.repo.EpsRepo;
import co.santahelena.his.repo.FacturaRepo;
import co.santahelena.his.repo.PazYSalvoRepo;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.FacturacionService;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.ReglaException;

@Controller
@RequestMapping("/facturacion")
public class FacturacionController {
    private final FacturacionService facturacion;
    private final CuentaRepo cuentas;
    private final FacturaRepo facturas;
    private final EpsRepo eps;
    private final AdmisionRepo admisiones;
    private final PazYSalvoRepo pazYSalvos;
    private final AuditoriaService auditoria;

    public FacturacionController(FacturacionService facturacion, CuentaRepo cuentas, FacturaRepo facturas, EpsRepo eps, AdmisionRepo admisiones,
                                 PazYSalvoRepo pazYSalvos, AuditoriaService auditoria) {
        this.facturacion = facturacion;
        this.cuentas = cuentas;
        this.facturas = facturas;
        this.eps = eps;
        this.admisiones = admisiones;
        this.pazYSalvos = pazYSalvos;
        this.auditoria = auditoria;
    }

    @GetMapping
    public String cuentas(Model model) {
        model.addAttribute("lista", cuentas.findByEstadoOrderByAbiertaEnDesc("ABIERTA"));
        return "facturacion/cuentas";
    }

    @GetMapping("/cuenta/{id}")
    public String cuenta(@PathVariable String id, Model model) {
        model.addAttribute("c", cuentas.findById(id).orElseThrow(() -> new ReglaException("Cuenta no encontrada.")));
        return "facturacion/cuenta";
    }

    @PostMapping("/cuenta/{id}/facturar")
    public String facturar(@PathVariable String id, RedirectAttributes ra) {
        Factura f = facturacion.facturar(id);
        ra.addFlashAttribute("ok", "Factura electrónica " + f.numero + " generada (envío DIAN simulado).");
        return "redirect:/facturacion/factura/" + f.id;
    }

    @GetMapping("/factura/{id}")
    public String factura(@PathVariable String id, Model model) {
        model.addAttribute("f", facturas.findById(id).orElseThrow(() -> new ReglaException("Factura no encontrada.")));
        return "imprimir/factura";
    }

    @PostMapping("/factura/{id}/pago")
    public String pago(@PathVariable String id, @RequestParam(required = false) String formaPago, RedirectAttributes ra) {
        facturacion.registrarPago(id, Form.s(formaPago));
        ra.addFlashAttribute("ok", "Pago registrado.");
        return "redirect:/facturacion/facturas";
    }

    @GetMapping("/facturas")
    public String facturas(@RequestParam(required = false) String q, Model model) {
        List<Factura> lista = new ArrayList<>();
        String n = Form.norm(q);
        for (Factura f : facturas.findAllByOrderByFechaDesc()) {
            if (n.isEmpty() || Form.norm(f.numero + " " + f.pacienteNombre + " " + f.pacienteDocumento + " " + f.epsNombre).contains(n)) {
                lista.add(f);
            }
        }
        model.addAttribute("lista", lista);
        model.addAttribute("q", q);
        return "facturacion/facturas";
    }

    @GetMapping("/convenios")
    public String convenios(Model model) {
        model.addAttribute("lista", eps.findAllByOrderByNombreAsc());
        return "facturacion/convenios";
    }

    @PostMapping("/convenios/guardar")
    public String guardarConvenio(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        String id = Form.s(f.get("id"));
        Eps e = id == null ? new Eps() : eps.findById(id).orElseThrow();
        if (id == null) {
            e.codigo = Form.s(f.get("codigo"));
            e.nombre = Form.s(f.get("nombre"));
            e.nit = Form.s(f.get("nit"));
            e.tipo = Form.s(f.get("tipo"));
            if (e.codigo == null || e.nombre == null) {
                throw new ReglaException("Código y nombre de la entidad son obligatorios.");
            }
            if (eps.findByCodigo(e.codigo).isPresent()) {
                throw new ReglaException("Ya existe una entidad con el código " + e.codigo + ".");
            }
        }
        Double factor = Form.d(f.get("factorTarifa"));
        if (factor == null || factor <= 0 || factor > 3) {
            throw new ReglaException("El factor de tarifa debe estar entre 0,01 y 3.");
        }
        double antes = e.factorTarifa;
        e.factorTarifa = factor;
        e.activo = id == null || Form.si(f.get("activo"));
        eps.save(e);
        auditoria.registrar("CONVENIO", "/facturacion/convenios", null, null, e.nombre + " factor " + antes + " → " + factor);
        ra.addFlashAttribute("ok", "Convenio con " + e.nombre + " guardado. El nuevo factor aplica a los cargos futuros.");
        return "redirect:/facturacion/convenios";
    }

    public static class FilaPys {
        public Admision a;
        public String motivo;
        public PazYSalvo pys;
    }

    @GetMapping("/paz-y-salvo")
    public String pazYSalvo(Model model) {
        List<FilaPys> filas = new ArrayList<>();
        for (Admision a : admisiones.findByEstadoOrderByFechaIngresoAsc("EN_CAMA")) {
            if (!a.ordenEgreso) {
                continue;
            }
            FilaPys f = new FilaPys();
            f.a = a;
            f.pys = pazYSalvos.findFirstByAdmisionId(a.id).orElse(null);
            f.motivo = f.pys == null ? facturacion.motivoSinPazYSalvo(a) : null;
            filas.add(f);
        }
        for (Admision a : admisiones.findByTipoOrderByFechaIngresoDesc("URGENCIAS")) {
            if (!"ATENDIDO".equals(a.estado) || a.fechaEgreso == null || a.fechaEgreso.isBefore(LocalDate.now().minusDays(2).atStartOfDay())) {
                continue;
            }
            FilaPys f = new FilaPys();
            f.a = a;
            f.pys = pazYSalvos.findFirstByAdmisionId(a.id).orElse(null);
            f.motivo = f.pys == null ? facturacion.motivoSinPazYSalvo(a) : null;
            filas.add(f);
        }
        model.addAttribute("filas", filas);
        model.addAttribute("expedidos", pazYSalvos.findAllByOrderByFechaDesc());
        return "facturacion/paz-y-salvo";
    }

    @PostMapping("/paz-y-salvo/{admisionId}")
    public String expedir(@PathVariable String admisionId, RedirectAttributes ra) {
        PazYSalvo p = facturacion.expedirPazYSalvo(admisionId);
        ra.addFlashAttribute("ok", "Paz y salvo " + p.numero + " expedido.");
        return "redirect:/facturacion/paz-y-salvo/" + p.id + "/imprimir";
    }

    @GetMapping("/paz-y-salvo/{id}/imprimir")
    public String imprimirPys(@PathVariable String id, Model model) {
        model.addAttribute("pys", pazYSalvos.findById(id).orElseThrow(() -> new ReglaException("Paz y salvo no encontrado.")));
        return "imprimir/pazysalvo";
    }

    @GetMapping("/rips")
    public String rips(Model model) {
        model.addAttribute("desde", LocalDate.now().withDayOfMonth(1));
        model.addAttribute("hasta", LocalDate.now());
        return "facturacion/rips";
    }

    @GetMapping("/rips/descargar")
    public ResponseEntity<byte[]> descargarRips(@RequestParam String desde, @RequestParam String hasta) throws Exception {
        LocalDate d = Form.fecha(desde);
        LocalDate h = Form.fecha(hasta);
        if (d == null || h == null || d.isAfter(h)) {
            throw new ReglaException("Rango de fechas inválido.");
        }
        Map<String, Object> data = facturacion.rips(d, h);
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        byte[] json = om.writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=RIPS_" + d + "_" + h + ".json")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json);
    }
}
