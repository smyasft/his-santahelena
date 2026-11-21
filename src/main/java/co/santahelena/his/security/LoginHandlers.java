package co.santahelena.his.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import co.santahelena.his.model.Usuario;
import co.santahelena.his.repo.UsuarioRepo;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.ConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Éxito: reinicia intentos y, si el rol exige MFA, deja la sesión pendiente del segundo factor.
 * Fallo: suma intentos; al quinto bloquea la cuenta 15 minutos (US-001).
 */
@Component
public class LoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    public static final String MFA_PENDIENTE = "MFA_PENDIENTE";
    public static final int MAX_INTENTOS = 5;
    public static final int MINUTOS_BLOQUEO = 15;

    private final UsuarioRepo usuarios;
    private final AuditoriaService auditoria;
    private final ConfigService config;

    public LoginHandlers(UsuarioRepo usuarios, AuditoriaService auditoria, ConfigService config) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.config = config;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication auth) throws IOException {
        Usuario u = usuarios.findByUsername(auth.getName()).orElseThrow();
        u.intentosFallidos = 0;
        u.bloqueadoHasta = null;
        u.ultimoAcceso = LocalDateTime.now();
        usuarios.save(u);
        Set<String> conMfa = new HashSet<>(config.get().rolesConMfa);
        conMfa.retainAll(u.roles);
        auditoria.registrarComo(u.username, String.join(",", u.roles), "LOGIN", "/login",
            conMfa.isEmpty() ? "Ingreso exitoso" : "Contraseña correcta, pendiente segundo factor");
        if (!conMfa.isEmpty()) {
            request.getSession().setAttribute(MFA_PENDIENTE, Boolean.TRUE);
            response.sendRedirect(request.getContextPath() + "/mfa");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        String username = request.getParameter("username");
        username = username == null ? "" : username.trim().toLowerCase();
        String motivo;
        if (ex instanceof LockedException) {
            motivo = "bloqueado";
        } else if (ex instanceof DisabledException) {
            motivo = "inactivo";
        } else {
            motivo = "credenciales";
            Usuario u = usuarios.findByUsername(username).orElse(null);
            if (u != null) {
                u.intentosFallidos++;
                if (u.intentosFallidos >= MAX_INTENTOS) {
                    u.bloqueadoHasta = LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO);
                    u.intentosFallidos = 0;
                    motivo = "bloqueado";
                    auditoria.registrarComo(username, String.join(",", u.roles), "BLOQUEO_CUENTA", "/login",
                        "Cuenta bloqueada " + MINUTOS_BLOQUEO + " minutos por " + MAX_INTENTOS + " intentos fallidos");
                }
                usuarios.save(u);
            }
        }
        auditoria.registrarComo(username, "", "LOGIN_FALLIDO", "/login", "Motivo: " + motivo);
        response.sendRedirect(request.getContextPath() + "/login?error=" + motivo);
    }
}
