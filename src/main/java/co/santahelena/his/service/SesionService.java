package co.santahelena.his.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import co.santahelena.his.model.Usuario;
import co.santahelena.his.repo.UsuarioRepo;
import co.santahelena.his.util.ReglaException;

/** Devuelve el usuario autenticado (se guarda en la petición para no consultarlo varias veces). */
@Service
public class SesionService {
    private static final String CLAVE = "HIS_USUARIO";
    private final UsuarioRepo usuarios;

    public SesionService(UsuarioRepo usuarios) {
        this.usuarios = usuarios;
    }

    public Usuario usuario() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            return null;
        }
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra != null) {
            Object c = ra.getAttribute(CLAVE, RequestAttributes.SCOPE_REQUEST);
            if (c instanceof Usuario u) {
                return u;
            }
        }
        Usuario u = usuarios.findByUsername(a.getName()).orElse(null);
        if (ra != null && u != null) {
            ra.setAttribute(CLAVE, u, RequestAttributes.SCOPE_REQUEST);
        }
        return u;
    }

    public Usuario requerido() {
        Usuario u = usuario();
        if (u == null) {
            throw new ReglaException("La sesión expiró. Ingrese de nuevo.");
        }
        return u;
    }

    public String nombre() {
        Usuario u = usuario();
        return u == null ? "sistema" : u.nombre;
    }
}
