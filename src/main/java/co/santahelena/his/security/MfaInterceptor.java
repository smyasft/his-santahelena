package co.santahelena.his.security;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Mientras el segundo factor esté pendiente, solo se permite la pantalla /mfa. */
@Component
public class MfaInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession s = request.getSession(false);
        if (s == null || !Boolean.TRUE.equals(s.getAttribute(LoginHandlers.MFA_PENDIENTE))) {
            return true;
        }
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        if (uri.startsWith("/mfa")) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/mfa");
        return false;
    }
}
