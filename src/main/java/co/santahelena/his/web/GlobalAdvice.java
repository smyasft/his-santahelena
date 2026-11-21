package co.santahelena.his.web;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import co.santahelena.his.model.Usuario;
import co.santahelena.his.service.AlertaService;
import co.santahelena.his.service.ConfigService;
import co.santahelena.his.service.MenuService;
import co.santahelena.his.service.SesionService;
import co.santahelena.his.util.ReglaException;
import jakarta.servlet.http.HttpServletRequest;

/** Datos comunes para todas las vistas y manejo uniforme de reglas de negocio incumplidas. */
@ControllerAdvice
public class GlobalAdvice {
    private final SesionService sesion;
    private final MenuService menu;
    private final AlertaService alertas;
    private final ConfigService config;

    public GlobalAdvice(SesionService sesion, MenuService menu, AlertaService alertas, ConfigService config) {
        this.sesion = sesion;
        this.menu = menu;
        this.alertas = alertas;
        this.config = config;
    }

    @ModelAttribute
    public void comun(Model model, HttpServletRequest req) {
        model.addAttribute("uri", req.getRequestURI().substring(req.getContextPath().length()));
        Usuario u = sesion.usuario();
        model.addAttribute("yo", u);
        if (u != null) {
            model.addAttribute("menu", menu.menu(u));
            model.addAttribute("alertasNoLeidas", alertas.contarNoLeidas(u));
            String[] b = menu.busqueda(u);
            if (b != null) {
                model.addAttribute("rutaBusqueda", b[0]);
                model.addAttribute("textoBusqueda", b[1]);
            }
        }
        model.addAttribute("cfg", config.get());
    }

    /**
     * Regla de negocio incumplida: en un formulario (POST) vuelve a la pantalla anterior con el mensaje en rojo;
     * al abrir una página (GET) muestra el mensaje en una página de aviso.
     */
    @ExceptionHandler(ReglaException.class)
    public ModelAndView regla(ReglaException e, HttpServletRequest req) {
        if ("GET".equalsIgnoreCase(req.getMethod())) {
            ModelAndView mv = new ModelAndView("aviso");
            mv.addObject("uri", req.getRequestURI().substring(req.getContextPath().length()));
            Usuario u = sesion.usuario();
            mv.addObject("yo", u);
            if (u != null) {
                mv.addObject("menu", menu.menu(u));
                mv.addObject("alertasNoLeidas", alertas.contarNoLeidas(u));
            }
            mv.addObject("cfg", config.get());
            mv.addObject("mensaje", e.getMessage());
            return mv;
        }
        FlashMap fm = RequestContextUtils.getOutputFlashMap(req);
        fm.put("error", e.getMessage());
        String ref = req.getHeader("Referer");
        return new ModelAndView("redirect:" + (ref != null && !ref.isBlank() ? ref : "/"));
    }
}
