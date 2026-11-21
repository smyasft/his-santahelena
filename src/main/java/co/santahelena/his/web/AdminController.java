package co.santahelena.his.web;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.santahelena.his.model.Auditoria;
import co.santahelena.his.model.CatalogoItem;
import co.santahelena.his.model.Configuracion;
import co.santahelena.his.model.Modulo;
import co.santahelena.his.model.Rol;
import co.santahelena.his.model.Usuario;
import co.santahelena.his.repo.CatalogoRepo;
import co.santahelena.his.repo.UsuarioRepo;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.ConfigService;
import co.santahelena.his.service.PermisoService;
import co.santahelena.his.service.SesionService;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.ReglaException;
import co.santahelena.his.util.Reglas;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final UsuarioRepo usuarios;
    private final CatalogoRepo catalogos;
    private final PermisoService permisos;
    private final ConfigService config;
    private final AuditoriaService auditoria;
    private final SesionService sesion;
    private final PasswordEncoder encoder;
    private final MongoTemplate mongo;

    public AdminController(UsuarioRepo usuarios, CatalogoRepo catalogos, PermisoService permisos, ConfigService config, AuditoriaService auditoria,
                           SesionService sesion, PasswordEncoder encoder, MongoTemplate mongo) {
        this.usuarios = usuarios;
        this.catalogos = catalogos;
        this.permisos = permisos;
        this.config = config;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.encoder = encoder;
        this.mongo = mongo;
    }

    @GetMapping
    public String raiz() {
        return "redirect:/admin/usuarios";
    }

    @GetMapping("/usuarios")
    public String usuarios(@RequestParam(required = false) String q, @RequestParam(required = false) String rol, Model model) {
        Query query = new Query().with(Sort.by("nombre"));
        if (Form.s(q) != null) {
            query.addCriteria(new Criteria().orOperator(
                Criteria.where("nombre").regex(Pattern.quote(q.trim()), "i"),
                Criteria.where("username").regex(Pattern.quote(q.trim()), "i"),
                Criteria.where("documento").regex(Pattern.quote(q.trim()))));
        }
        if (Form.s(rol) != null) {
            query.addCriteria(Criteria.where("roles").is(rol));
        }
        model.addAttribute("lista", mongo.find(query, Usuario.class));
        model.addAttribute("roles", Rol.values());
        model.addAttribute("q", q);
        model.addAttribute("rolSel", rol);
        return "admin/usuarios";
    }

    @GetMapping("/usuarios/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("u", new Usuario());
        model.addAttribute("roles", Rol.values());
        return "admin/usuario-form";
    }

    @GetMapping("/usuarios/{id}")
    public String editar(@PathVariable String id, Model model) {
        model.addAttribute("u", usuarios.findById(id).orElseThrow(() -> new ReglaException("Usuario no encontrado.")));
        model.addAttribute("roles", Rol.values());
        return "admin/usuario-form";
    }

    @PostMapping("/usuarios/guardar")
    public String guardar(@RequestParam MultiValueMap<String, String> f, RedirectAttributes ra) {
        String id = F.v(f, "id");
        boolean nuevo = id == null;
        Usuario u = nuevo ? new Usuario() : usuarios.findById(id).orElseThrow();
        String username = F.v(f, "username");
        if (username == null || !username.matches("[a-zA-Z0-9._-]{3,30}")) {
            throw new ReglaException("Usuario inválido (3 a 30 caracteres: letras, números, punto o guion).");
        }
        username = username.toLowerCase();
        Usuario otro = usuarios.findByUsername(username).orElse(null);
        if (otro != null && !otro.id.equals(u.id)) {
            throw new ReglaException("El nombre de usuario ya existe.");
        }
        String doc = F.v(f, "documento");
        if (doc != null) {
            Usuario o2 = usuarios.findByDocumento(doc).orElse(null);
            if (o2 != null && !o2.id.equals(u.id)) {
                throw new ReglaException("Ya existe un usuario con el documento " + doc + ".");
            }
        }
        Set<String> roles = new LinkedHashSet<>(F.l(f, "roles"));
        if (roles.isEmpty()) {
            throw new ReglaException("Asigne al menos un rol.");
        }
        Usuario yo = sesion.requerido();
        if (yo.id.equals(u.id) && !roles.contains("ADMIN")) {
            throw new ReglaException("No puede quitarse a sí mismo el rol de administrador.");
        }
        String clave = F.v(f, "password");
        if (nuevo && clave == null) {
            throw new ReglaException("Asigne una contraseña inicial.");
        }
        if (clave != null) {
            String pol = Reglas.politicaPassword(clave);
            if (pol != null) {
                throw new ReglaException(pol);
            }
            u.passwordHash = encoder.encode(clave);
        }
        Set<String> antes = new LinkedHashSet<>(u.roles);
        u.username = username;
        u.nombre = F.v(f, "nombre");
        u.documento = doc;
        u.email = F.v(f, "email") == null ? null : F.v(f, "email").toLowerCase();
        u.roles = roles;
        u.activo = Form.si(F.v(f, "activo")) || nuevo;
        u.especialidad = F.v(f, "especialidad");
        u.registroMedico = F.v(f, "registroMedico");
        if (u.nombre == null) {
            throw new ReglaException("El nombre es obligatorio.");
        }
        usuarios.save(u);
        auditoria.registrar(nuevo ? "USUARIO_CREADO" : "USUARIO_MODIFICADO", "/admin/usuarios", null, null,
            u.username + " roles " + antes + " → " + roles + (clave != null ? " (contraseña asignada)" : ""));
        ra.addFlashAttribute("ok", "Usuario " + u.username + " guardado.");
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/{id}/estado")
    public String estado(@PathVariable String id, RedirectAttributes ra) {
        Usuario u = usuarios.findById(id).orElseThrow();
        if (u.id.equals(sesion.requerido().id)) {
            throw new ReglaException("No puede desactivar su propio usuario.");
        }
        u.activo = !u.activo;
        usuarios.save(u);
        auditoria.registrar(u.activo ? "USUARIO_ACTIVADO" : "USUARIO_DESACTIVADO", "/admin/usuarios", null, null, u.username);
        ra.addFlashAttribute("ok", "Usuario " + u.username + (u.activo ? " activado." : " desactivado."));
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/{id}/desbloquear")
    public String desbloquear(@PathVariable String id, RedirectAttributes ra) {
        Usuario u = usuarios.findById(id).orElseThrow();
        u.bloqueadoHasta = null;
        u.intentosFallidos = 0;
        usuarios.save(u);
        auditoria.registrar("USUARIO_DESBLOQUEADO", "/admin/usuarios", null, null, u.username);
        ra.addFlashAttribute("ok", "Usuario " + u.username + " desbloqueado.");
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/{id}/reiniciar-mfa")
    public String reiniciarMfa(@PathVariable String id, RedirectAttributes ra) {
        Usuario u = usuarios.findById(id).orElseThrow();
        u.mfaSecreto = null;
        u.mfaConfigurado = false;
        usuarios.save(u);
        auditoria.registrar("MFA_REINICIADO", "/admin/usuarios", null, null, u.username);
        ra.addFlashAttribute("ok", "El segundo factor de " + u.username + " se configurará de nuevo en su próximo ingreso.");
        return "redirect:/admin/usuarios";
    }

    @GetMapping("/permisos")
    public String permisos(Model model) {
        model.addAttribute("matriz", permisos.matriz());
        model.addAttribute("roles", Rol.values());
        model.addAttribute("modulos", Modulo.values());
        return "admin/permisos";
    }

    @PostMapping("/permisos")
    public String guardarPermisos(@RequestParam MultiValueMap<String, String> f, RedirectAttributes ra) {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        for (Rol r : Rol.values()) {
            Set<String> s = new LinkedHashSet<>();
            for (Modulo mo : Modulo.values()) {
                if (f.containsKey("p_" + r.name() + "_" + mo.name())) {
                    s.add(mo.name());
                }
            }
            m.put(r.name(), s);
        }
        if (!m.get("ADMIN").contains("SEGURIDAD")) {
            throw new ReglaException("El rol Administrador debe conservar el módulo de Seguridad para no perder el acceso.");
        }
        Map<String, Set<String>> antes = permisos.matriz();
        permisos.guardar(m);
        auditoria.registrar("PERMISOS_MODIFICADOS", "/admin/permisos", null, null, "Antes: " + antes + " | Después: " + m);
        ra.addFlashAttribute("ok", "Matriz de permisos actualizada. Aplica desde la siguiente petición de cada usuario.");
        return "redirect:/admin/permisos";
    }

    @GetMapping("/auditoria")
    public String auditoria(@RequestParam Map<String, String> f, Model model) {
        List<Auditoria> lista = auditoria.buscar(Form.s(f.get("usuario")), Form.s(f.get("accion")), Form.s(f.get("paciente")),
            Form.fecha(f.get("desde")), Form.fecha(f.get("hasta")), 500);
        model.addAttribute("lista", lista);
        model.addAttribute("acciones", auditoria.acciones());
        model.addAttribute("f", f);
        return "admin/auditoria";
    }

    @GetMapping("/auditoria.csv")
    public ResponseEntity<byte[]> exportar(@RequestParam Map<String, String> f) {
        List<Auditoria> lista = auditoria.buscar(Form.s(f.get("usuario")), Form.s(f.get("accion")), Form.s(f.get("paciente")),
            Form.fecha(f.get("desde")), Form.fecha(f.get("hasta")), 20000);
        StringBuilder sb = new StringBuilder("﻿fecha;usuario;roles;accion;recurso;paciente;ip;detalle\n");
        for (Auditoria a : lista) {
            sb.append(a.fecha).append(';').append(c(a.usuario)).append(';').append(c(a.roles)).append(';').append(c(a.accion)).append(';')
                .append(c(a.recurso)).append(';').append(c(a.pacienteNombre)).append(';').append(c(a.ip)).append(';').append(c(a.detalle)).append('\n');
        }
        auditoria.registrar("AUDITORIA_EXPORTADA", "/admin/auditoria.csv", null, null, lista.size() + " registros");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=auditoria_his.csv")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String c(String s) {
        return s == null ? "" : "\"" + s.replace("\"", "'").replace("\n", " ") + "\"";
    }

    @GetMapping("/seguridad")
    public String seguridad(Model model) {
        model.addAttribute("c", config.get());
        model.addAttribute("roles", Rol.values());
        return "admin/seguridad";
    }

    @PostMapping("/seguridad")
    public String guardarSeguridad(@RequestParam MultiValueMap<String, String> f, RedirectAttributes ra) {
        Configuracion c = config.get();
        c.razonSocial = F.v(f, "razonSocial");
        c.nit = F.v(f, "nit");
        c.codigoHabilitacion = F.v(f, "codigoHabilitacion");
        c.ciudad = F.v(f, "ciudad");
        c.rolesConMfa = new LinkedHashSet<>(F.l(f, "rolesConMfa"));
        c.taSistolicaMin = entero(f, "taSistolicaMin");
        c.taSistolicaMax = entero(f, "taSistolicaMax");
        c.taDiastolicaMin = entero(f, "taDiastolicaMin");
        c.taDiastolicaMax = entero(f, "taDiastolicaMax");
        c.fcMin = entero(f, "fcMin");
        c.fcMax = entero(f, "fcMax");
        c.frMin = entero(f, "frMin");
        c.frMax = entero(f, "frMax");
        c.saturacionMin = entero(f, "saturacionMin");
        c.glucometriaMin = entero(f, "glucometriaMin");
        c.glucometriaMax = entero(f, "glucometriaMax");
        Double tmin = Form.d(F.v(f, "temperaturaMin"));
        Double tmax = Form.d(F.v(f, "temperaturaMax"));
        if (tmin == null || tmax == null || tmin >= tmax) {
            throw new ReglaException("Rango de temperatura inválido.");
        }
        c.temperaturaMin = tmin;
        c.temperaturaMax = tmax;
        if (c.taSistolicaMin >= c.taSistolicaMax || c.fcMin >= c.fcMax || c.frMin >= c.frMax || c.glucometriaMin >= c.glucometriaMax) {
            throw new ReglaException("Cada mínimo debe ser menor que su máximo.");
        }
        config.guardar(c);
        auditoria.registrar("PARAMETROS_MODIFICADOS", "/admin/seguridad", null, null, "MFA para " + c.rolesConMfa);
        ra.addFlashAttribute("ok", "Parámetros guardados.");
        return "redirect:/admin/seguridad";
    }

    private int entero(MultiValueMap<String, String> f, String k) {
        Integer v = Form.i(F.v(f, k));
        if (v == null) {
            throw new ReglaException("Falta el valor de " + k + ".");
        }
        return v;
    }

    @GetMapping("/catalogos")
    public String catalogos(@RequestParam(defaultValue = "CIE10") String tipo, @RequestParam(required = false) String q, Model model) {
        Query query = Query.query(Criteria.where("tipo").is(tipo));
        if (Form.s(q) != null) {
            query.addCriteria(Criteria.where("busqueda").regex(Pattern.quote(Form.norm(q))));
        }
        query.with(Sort.by("codigo")).limit(200);
        model.addAttribute("lista", mongo.find(query, CatalogoItem.class));
        model.addAttribute("tipo", tipo);
        model.addAttribute("q", q);
        model.addAttribute("total", catalogos.countByTipo(tipo));
        return "admin/catalogos";
    }

    @PostMapping("/catalogos/agregar")
    public String agregar(@RequestParam String tipo, @RequestParam String codigo, @RequestParam String descripcion,
                          @RequestParam(required = false) String valor, RedirectAttributes ra) {
        upsert(tipo, codigo, descripcion, Form.d(valor));
        auditoria.registrar("CATALOGO_ITEM", "/admin/catalogos", null, null, tipo + " " + codigo);
        ra.addFlashAttribute("ok", "Código " + codigo + " guardado en " + tipo + ".");
        return "redirect:/admin/catalogos?tipo=" + tipo;
    }

    /** Carga masiva: CSV separado por punto y coma: codigo;descripcion;valor(opcional). */
    @PostMapping("/catalogos/importar")
    public String importar(@RequestParam String tipo, @RequestParam("archivo") MultipartFile archivo, RedirectAttributes ra) throws Exception {
        if (archivo.isEmpty()) {
            throw new ReglaException("Seleccione un archivo CSV.");
        }
        int n = 0;
        int errores = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.replace("﻿", "").trim();
                if (linea.isEmpty() || linea.toLowerCase().startsWith("codigo")) {
                    continue;
                }
                String[] p = linea.split(";");
                if (p.length < 2 || p[0].isBlank() || p[1].isBlank()) {
                    errores++;
                    continue;
                }
                upsert(tipo, p[0].trim(), p[1].trim(), p.length > 2 ? Form.d(p[2]) : null);
                n++;
            }
        }
        auditoria.registrar("CATALOGO_IMPORTADO", "/admin/catalogos", null, null, tipo + ": " + n + " registros, " + errores + " con error");
        ra.addFlashAttribute("ok", n + " registros importados en " + tipo + (errores > 0 ? " (" + errores + " líneas inválidas omitidas)" : "") + ".");
        return "redirect:/admin/catalogos?tipo=" + tipo;
    }

    private void upsert(String tipo, String codigo, String descripcion, Double valor) {
        CatalogoItem c = catalogos.findByTipoAndCodigo(tipo, codigo.toUpperCase()).orElse(null);
        CatalogoItem n = CatalogoItem.de(tipo, codigo.toUpperCase(), descripcion, valor);
        if (c != null) {
            n.id = c.id;
        }
        catalogos.save(n);
    }
}
