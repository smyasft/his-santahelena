package co.santahelena.his.security;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import co.santahelena.his.model.Modulo;
import co.santahelena.his.model.Usuario;
import co.santahelena.his.service.AuditoriaService;
import co.santahelena.his.service.PermisoService;
import co.santahelena.his.service.SesionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Verifica en cada petición que el rol tenga habilitado el módulo; si no, registra el intento y niega. */
@Component
public class PermisoInterceptor implements HandlerInterceptor {
    private final SesionService sesion;
    private final PermisoService permisos;
    private final AuditoriaService auditoria;

    public PermisoInterceptor(SesionService sesion, PermisoService permisos, AuditoriaService auditoria) {
        this.sesion = sesion;
        this.permisos = permisos;
        this.auditoria = auditoria;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        Modulo m = Modulo.deRuta(uri);
        if (m == null) {
            return true;
        }
        Usuario u = sesion.usuario();
        if (u == null || permisos.permitido(u, m)) {
            return true;
        }
        auditoria.registrar("ACCESO_DENEGADO", uri, null, null, "Módulo " + m.nombre + " no habilitado para " + u.rolesTexto());
        response.sendRedirect(request.getContextPath() + "/denegado?modulo=" + m.name());
        return false;
    }
}
