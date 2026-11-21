package co.santahelena.his.web;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.santahelena.his.model.Modulo;
import co.santahelena.his.model.Paciente;
import co.santahelena.his.model.Usuario;
import co.santahelena.his.repo.PacienteRepo;
import co.santahelena.his.repo.UsuarioRepo;
import co.santahelena.his.security.LoginHandlers;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.SesionService;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.Reglas;
import co.santahelena.his.util.Totp;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class AuthController {
    private final UsuarioRepo usuarios;
    private final PacienteRepo pacientes;
    private final PasswordEncoder encoder;
    private final SesionService sesion;
    private final AuditoriaService auditoria;

    public AuthController(UsuarioRepo usuarios, PacienteRepo pacientes, PasswordEncoder encoder, SesionService sesion, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.pacientes = pacientes;
        this.encoder = encoder;
        this.sesion = sesion;
        this.auditoria = auditoria;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Model model) {
        if (sesion.usuario() != null && error == null) {
            return "redirect:/";
        }
        String msg = null;
        if ("credenciales".equals(error)) {
            msg = "Usuario o contraseña incorrectos. Tras " + LoginHandlers.MAX_INTENTOS + " intentos la cuenta se bloquea " + LoginHandlers.MINUTOS_BLOQUEO + " minutos.";
        } else if ("bloqueado".equals(error)) {
            msg = "La cuenta está bloqueada temporalmente por intentos fallidos. Intente en " + LoginHandlers.MINUTOS_BLOQUEO + " minutos o contacte al administrador.";
        } else if ("inactivo".equals(error)) {
            msg = "El usuario está inactivo. Contacte al administrador del sistema.";
        }
        model.addAttribute("mensajeError", msg);
        return "auth/login";
    }

    @GetMapping("/registro")
    public String registro() {
        return "auth/registro";
    }

    /** Autorregistro del paciente al portal: debe existir su historia (creada en admisiones). */
    @PostMapping("/registro")
    public String registrar(@RequestParam Map<String, String> f, Model model, RedirectAttributes ra) {
        String tipo = Form.s(f.get("tipoDocumento"));
        String num = Form.s(f.get("numeroDocumento"));
        LocalDate nac = Form.fecha(f.get("fechaNacimiento"));
        String username = Form.s(f.get("username"));
        String email = Form.s(f.get("email"));
        String clave = f.get("password");
        String error = null;
        Paciente p = tipo == null || num == null ? null : pacientes.findByTipoDocumentoAndNumeroDocumento(tipo, num).orElse(null);
        if (p == null || nac == null || !nac.equals(p.fechaNacimiento)) {
            error = "No encontramos una historia clínica con esos datos. Acérquese a admisiones para registrarse primero.";
        } else if (usuarios.findByPacienteId(p.id).isPresent()) {
            error = "Este paciente ya tiene usuario en el portal. Use «¿Olvidó su contraseña?» en recepción.";
        } else if (username == null || !username.matches("[a-zA-Z0-9._-]{4,30}")) {
            error = "El usuario debe tener entre 4 y 30 caracteres (letras, números, punto, guion).";
        } else if (usuarios.findByUsername(username.toLowerCase()).isPresent()) {
            error = "El nombre de usuario ya está en uso.";
        } else if (email == null || !email.contains("@")) {
            error = "Escriba un correo electrónico válido.";
        } else if (usuarios.findByEmail(email.toLowerCase()).isPresent()) {
            error = "El correo ya está registrado.";
        } else if (Reglas.politicaPassword(clave) != null) {
            error = Reglas.politicaPassword(clave);
        } else if (!clave.equals(f.get("password2"))) {
            error = "Las contraseñas no coinciden.";
        } else if (!Form.si(f.get("acepta"))) {
            error = "Debe aceptar la política de tratamiento de datos personales (Ley 1581 de 2012).";
        }
        if (error != null) {
            model.addAttribute("mensajeError", error);
            model.addAttribute("f", f);
            return "auth/registro";
        }
        Usuario u = new Usuario();
        u.username = username.toLowerCase();
        u.nombre = p.nombreCompleto();
        u.documento = p.numeroDocumento;
        u.email = email.toLowerCase();
        u.roles.add("PACIENTE");
        u.pacienteId = p.id;
        u.passwordHash = encoder.encode(clave);
        usuarios.save(u);
        auditoria.registrarComo(u.username, "PACIENTE", "REGISTRO_PORTAL", "/registro", p.numeroHistoria);
        ra.addFlashAttribute("ok", "Cuenta creada. Ya puede ingresar al portal del paciente.");
        return "redirect:/login";
    }

    @GetMapping("/mfa")
    public String mfa(HttpSession session, Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute(LoginHandlers.MFA_PENDIENTE))) {
            return "redirect:/";
        }
        Usuario u = sesion.requerido();
        if (!u.mfaConfigurado) {
            if (u.mfaSecreto == null) {
                u.mfaSecreto = Totp.nuevoSecreto();
                usuarios.save(u);
            }
            model.addAttribute("secreto", Totp.agrupado(u.mfaSecreto));
            model.addAttribute("otpUri", Totp.uri(u.mfaSecreto, u.username));
        }
        return "auth/mfa";
    }

    @PostMapping("/mfa")
    public String verificarMfa(@RequestParam String codigo, HttpSession session, RedirectAttributes ra) {
        Usuario u = sesion.requerido();
        if (u.mfaSecreto != null && Totp.verificar(u.mfaSecreto, codigo.replace(" ", ""))) {
            u.mfaConfigurado = true;
            u.intentosFallidos = 0;
            usuarios.save(u);
            session.removeAttribute(LoginHandlers.MFA_PENDIENTE);
            auditoria.registrar("MFA_OK", "/mfa", null, null, "Segundo factor verificado");
            return "redirect:/";
        }
        u.intentosFallidos++;
        if (u.intentosFallidos >= LoginHandlers.MAX_INTENTOS) {
            u.intentosFallidos = 0;
            u.bloqueadoHasta = java.time.LocalDateTime.now().plusMinutes(LoginHandlers.MINUTOS_BLOQUEO);
            usuarios.save(u);
            auditoria.registrar("BLOQUEO_CUENTA", "/mfa", null, null, "Códigos MFA incorrectos");
            session.invalidate();
            return "redirect:/login?error=bloqueado";
        }
        usuarios.save(u);
        auditoria.registrar("MFA_FALLIDO", "/mfa", null, null, "Código incorrecto");
        ra.addFlashAttribute("error", "Código incorrecto o vencido. Verifique la hora de su celular.");
        return "redirect:/mfa";
    }

    @GetMapping("/denegado")
    public String denegado(@RequestParam(required = false) String modulo, Model model, HttpServletResponse resp) {
        resp.setStatus(403);
        String nombre = null;
        if (modulo != null) {
            for (Modulo m : Modulo.values()) {
                if (m.name().equals(modulo)) {
                    nombre = m.nombre;
                }
            }
        }
        model.addAttribute("modulo", nombre);
        return "auth/denegado";
    }
}
