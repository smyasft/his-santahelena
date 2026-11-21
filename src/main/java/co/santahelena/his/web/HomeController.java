package co.santahelena.his.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.santahelena.his.model.Admision;
import co.santahelena.his.model.Alerta;
import co.santahelena.his.model.CatalogoItem;
import co.santahelena.his.model.Cita;
import co.santahelena.his.model.Cuenta;
import co.santahelena.his.model.OrdenLab;
import co.santahelena.his.model.Prescripcion;
import co.santahelena.his.model.Usuario;
import co.santahelena.his.repo.CamaRepo;
import co.santahelena.his.repo.UsuarioRepo;
import co.santahelena.his.service.AlertaService;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.DashboardService;
import co.santahelena.his.service.MenuService;
import co.santahelena.his.service.PermisoService;
import co.santahelena.his.service.SesionService;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.ReglaException;
import co.santahelena.his.util.Reglas;

@Controller
public class HomeController {
    private final SesionService sesion;
    private final MenuService menu;
    private final PermisoService permisos;
    private final AlertaService alertas;
    private final MongoTemplate mongo;
    private final CamaRepo camas;
    private final UsuarioRepo usuarios;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final DashboardService tablero;

    public HomeController(SesionService sesion, MenuService menu, PermisoService permisos, AlertaService alertas, MongoTemplate mongo,
                          CamaRepo camas, UsuarioRepo usuarios, PasswordEncoder encoder, AuditoriaService auditoria, DashboardService tablero) {
        this.tablero = tablero;
        this.sesion = sesion;
        this.menu = menu;
        this.permisos = permisos;
        this.alertas = alertas;
        this.mongo = mongo;
        this.camas = camas;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    public static class Indicador {
        public final String titulo;
        public final String valor;
        public final String detalle;
        public final String icono;
        public final String enlace;
        public final String tono;

        Indicador(String titulo, String valor, String detalle, String icono, String enlace, String tono) {
            this.titulo = titulo;
            this.valor = valor;
            this.detalle = detalle;
            this.icono = icono;
            this.enlace = enlace;
            this.tono = tono;
        }
    }

    private long contar(Criteria c, Class<?> tipo) {
        return mongo.count(Query.query(c), tipo);
    }

    @GetMapping("/")
    public String inicio(Model model) {
        Usuario u = sesion.requerido();
        Set<String> mods = permisos.modulos(u);
        if (mods.size() == 1 && mods.contains("PORTAL")) {
            return "redirect:/portal";
        }
        LocalDate hoy = LocalDate.now();
        List<Indicador> ind = new ArrayList<>();
        if (mods.contains("ADMISIONES")) {
            long c = contar(Criteria.where("fechaHora").gte(hoy.atStartOfDay()).lt(hoy.plusDays(1).atStartOfDay()).and("estado").ne("CANCELADA"), Cita.class);
            ind.add(new Indicador("Citas de hoy", String.valueOf(c), "Todas las agendas", "bi-calendar-week", "/admisiones/citas", "azul"));
        }
        if (mods.contains("ENFERMERIA") || mods.contains("ADMISIONES")) {
            long c = contar(Criteria.where("tipo").is("URGENCIAS").and("estado").is("EN_ESPERA_TRIAJE"), Admision.class);
            ind.add(new Indicador("En espera de triaje", String.valueOf(c), "Urgencias", "bi-hourglass-split", mods.contains("ENFERMERIA") ? "/enfermeria" : "/admisiones/urgencias", c > 0 ? "rojo" : "verde"));
        }
        if (mods.contains("HCE")) {
            long c = contar(Criteria.where("tipo").is("URGENCIAS").and("estado").in("TRIADO", "EN_ATENCION"), Admision.class);
            long cit = contar(Criteria.where("profesionalId").is(u.id).and("fechaHora").gte(hoy.atStartOfDay()).lt(hoy.plusDays(1).atStartOfDay()).and("estado").is("ASIGNADA"), Cita.class);
            ind.add(new Indicador("Urgencias por atender", String.valueOf(c), "Pacientes clasificados", "bi-truck-front", "/hce", c > 0 ? "naranja" : "verde"));
            ind.add(new Indicador("Mis citas pendientes hoy", String.valueOf(cit), u.especialidad == null ? "" : u.especialidad, "bi-person-lines-fill", "/hce", "azul"));
        }
        if (mods.contains("LABORATORIO")) {
            long c = contar(Criteria.where("estado").in("ORDENADA", "RECIBIDA", "RESULTADOS"), OrdenLab.class);
            ind.add(new Indicador("Órdenes en proceso", String.valueOf(c), "Laboratorio", "bi-eyedropper", "/laboratorio", "azul"));
        }
        if (mods.contains("FARMACIA")) {
            long c = contar(Criteria.where("estado").is("ACTIVA").and("validacionFarmacia").is("PENDIENTE"), Prescripcion.class);
            ind.add(new Indicador("Fórmulas por validar", String.valueOf(c), "Farmacia", "bi-prescription2", "/farmacia", c > 0 ? "naranja" : "verde"));
        }
        if (mods.contains("FACTURACION")) {
            long c = contar(Criteria.where("estado").is("ABIERTA"), Cuenta.class);
            ind.add(new Indicador("Cuentas abiertas", String.valueOf(c), "Pendientes de facturar", "bi-receipt", "/facturacion", "azul"));
        }
        if (mods.contains("CAMAS")) {
            long total = camas.count();
            long ocup = camas.findByEstado("OCUPADA").size();
            long pend = contar(Criteria.where("estado").is("PENDIENTE_CAMA"), Admision.class);
            ind.add(new Indicador("Ocupación", total == 0 ? "0 %" : (ocup * 100 / total) + " %", ocup + " de " + total + " camas", "bi-hospital", "/camas", "azul"));
            ind.add(new Indicador("Pendientes de cama", String.valueOf(pend), "Órdenes de hospitalización", "bi-hourglass", "/camas/pendientes", pend > 0 ? "naranja" : "verde"));
        }
        if (mods.contains("SEGURIDAD")) {
            long activos = contar(Criteria.where("activo").is(true), Usuario.class);
            long bloqueados = contar(Criteria.where("bloqueadoHasta").gt(java.time.LocalDateTime.now()), Usuario.class);
            ind.add(new Indicador("Usuarios activos", String.valueOf(activos), bloqueados + " bloqueado(s)", "bi-people", "/admin/usuarios", "azul"));
        }
        model.addAttribute("indicadores", ind);
        model.addAttribute("modulos", menu.menu(u));
        model.addAttribute("ultimasAlertas", alertas.recientes(u, 5));
        // Gráficas del tablero: el médico ve su propia agenda; los demás roles, la de toda la clínica
        String prof = mods.contains("HCE") ? u.id : null;
        model.addAttribute("agendaPropia", prof != null);
        model.addAttribute("donaCamas", tablero.camas());
        model.addAttribute("donaTriaje", tablero.triaje());
        List<DashboardService.Barra> semana = tablero.citasSemana(prof);
        model.addAttribute("semana", semana);
        model.addAttribute("totalSemana", semana.stream().mapToLong(b -> b.valor).sum());
        model.addAttribute("mes", tablero.calendario(prof));
        model.addAttribute("agendaHoy", tablero.agendaHoy(prof, 6));
        model.addAttribute("ahora", java.time.LocalDateTime.now());
        return "inicio";
    }

    @GetMapping("/alertas")
    public String alertas(Model model) {
        model.addAttribute("lista", alertas.recientes(sesion.requerido(), 100));
        return "alertas";
    }

    @PostMapping("/alertas/{id}/leer")
    public String leer(@PathVariable String id) {
        Usuario u = sesion.requerido();
        alertas.marcarLeida(id, u);
        Alerta a = mongo.findById(id, Alerta.class);
        return "redirect:" + (a != null && a.enlace != null ? a.enlace : "/alertas");
    }

    @PostMapping("/alertas/leer-todas")
    public String leerTodas() {
        alertas.marcarTodas(sesion.requerido());
        return "redirect:/alertas";
    }

    /** Autocompletado de catálogos (CIE-10 en la HCE, CUPS en cirugía). */
    @GetMapping("/api/catalogo/{tipo}")
    @ResponseBody
    public List<Map<String, String>> catalogo(@PathVariable String tipo, @RequestParam(defaultValue = "") String q) {
        String n = Form.norm(q);
        Query query = Query.query(Criteria.where("tipo").is(tipo.toUpperCase()));
        if (!n.isEmpty()) {
            query.addCriteria(Criteria.where("busqueda").regex(java.util.regex.Pattern.quote(n)));
        }
        query.with(Sort.by("codigo")).limit(20);
        List<Map<String, String>> r = new ArrayList<>();
        for (CatalogoItem c : mongo.find(query, CatalogoItem.class)) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("codigo", c.codigo);
            m.put("descripcion", c.descripcion);
            r.add(m);
        }
        return r;
    }

    @GetMapping("/perfil")
    public String perfil() {
        return "perfil";
    }

    @PostMapping("/perfil/clave")
    public String cambiarClave(@RequestParam String actual, @RequestParam String nueva, @RequestParam String confirmar, RedirectAttributes ra) {
        Usuario u = sesion.requerido();
        if (!encoder.matches(actual, u.passwordHash)) {
            throw new ReglaException("La contraseña actual no es correcta.");
        }
        String p = Reglas.politicaPassword(nueva);
        if (p != null) {
            throw new ReglaException(p);
        }
        if (!nueva.equals(confirmar)) {
            throw new ReglaException("La confirmación no coincide.");
        }
        u.passwordHash = encoder.encode(nueva);
        usuarios.save(u);
        auditoria.registrar("CAMBIO_CLAVE", "/perfil", null, null, "Cambio de contraseña por el usuario");
        ra.addFlashAttribute("ok", "Contraseña actualizada.");
        return "redirect:/perfil";
    }
}
