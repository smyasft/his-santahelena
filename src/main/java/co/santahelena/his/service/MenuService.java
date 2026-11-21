package co.santahelena.his.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import co.santahelena.his.model.Modulo;
import co.santahelena.his.model.Usuario;

/** Arma el menú lateral según los módulos que el rol tiene habilitados. */
@Service
public class MenuService {
    public static class Item {
        public final String etiqueta;
        public final String ruta;
        public final String icono;

        Item(String etiqueta, String ruta, String icono) {
            this.etiqueta = etiqueta;
            this.ruta = ruta;
            this.icono = icono;
        }

        /** Resalta la opción actual; las rutas raíz del módulo solo se marcan con coincidencia exacta. */
        public boolean activo(String uri) {
            if (uri == null) {
                return false;
            }
            if (uri.equals(ruta)) {
                return true;
            }
            return ruta.indexOf('/', 1) > 0 && uri.startsWith(ruta + "/");
        }
    }

    public static class Grupo {
        public final Modulo modulo;
        public final String icono;
        public final List<Item> items;

        Grupo(Modulo modulo, String icono, List<Item> items) {
            this.modulo = modulo;
            this.icono = icono;
            this.items = items;
        }
    }

    private static final Map<Modulo, Grupo> GRUPOS = new LinkedHashMap<>();

    private static void g(Modulo m, String icono, String... datos) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < datos.length; i += 3) {
            items.add(new Item(datos[i], datos[i + 1], datos[i + 2]));
        }
        GRUPOS.put(m, new Grupo(m, icono, items));
    }

    static {
        g(Modulo.ADMISIONES, "bi-person-vcard",
            "Buscar paciente", "/admisiones", "bi-search",
            "Registrar paciente", "/admisiones/paciente/nuevo", "bi-person-plus",
            "Citas", "/admisiones/citas", "bi-calendar-week",
            "Ingreso a urgencias", "/admisiones/urgencias", "bi-truck-front");
        g(Modulo.PORTAL, "bi-house-heart",
            "Mi inicio", "/portal", "bi-house",
            "Mis citas", "/portal/citas", "bi-calendar-check",
            "Resultados", "/portal/resultados", "bi-clipboard2-pulse",
            "Fórmulas", "/portal/formulas", "bi-capsule");
        g(Modulo.ENFERMERIA, "bi-heart-pulse",
            "Cola de triaje", "/enfermeria", "bi-list-ol",
            "Pacientes hospitalizados", "/enfermeria/pacientes", "bi-hospital");
        g(Modulo.HCE, "bi-journal-medical",
            "Mi agenda", "/hce", "bi-calendar-day",
            "Buscar historia", "/hce/buscar", "bi-search");
        g(Modulo.ESPECIALIDADES, "bi-clipboard2-heart",
            "Interconsultas", "/especialista", "bi-arrow-left-right",
            "Consentimientos", "/especialista/consentimientos", "bi-pen",
            "Programación quirúrgica", "/especialista/cirugias", "bi-scissors");
        g(Modulo.LABORATORIO, "bi-eyedropper",
            "Órdenes", "/laboratorio", "bi-inboxes",
            "Validados", "/laboratorio/validados", "bi-patch-check",
            "Catálogo de exámenes", "/laboratorio/examenes", "bi-list-check");
        g(Modulo.FARMACIA, "bi-capsule-pill",
            "Fórmulas pendientes", "/farmacia", "bi-prescription2",
            "Inventario", "/farmacia/inventario", "bi-box-seam",
            "Catálogo INVIMA", "/farmacia/invima", "bi-cloud-download",
            "Control especial", "/farmacia/control", "bi-shield-lock");
        g(Modulo.FACTURACION, "bi-cash-coin",
            "Cuentas abiertas", "/facturacion", "bi-receipt",
            "Facturas", "/facturacion/facturas", "bi-file-earmark-text",
            "Paz y salvo", "/facturacion/paz-y-salvo", "bi-check2-square",
            "Convenios EPS", "/facturacion/convenios", "bi-building",
            "RIPS", "/facturacion/rips", "bi-filetype-json");
        g(Modulo.CAMAS, "bi-hospital",
            "Mapa de camas", "/camas", "bi-grid-3x3-gap",
            "Pendientes de cama", "/camas/pendientes", "bi-hourglass-split");
        g(Modulo.SEGURIDAD, "bi-shield-check",
            "Usuarios", "/admin/usuarios", "bi-people",
            "Permisos por rol", "/admin/permisos", "bi-grid-3x3",
            "Auditoría", "/admin/auditoria", "bi-journal-text",
            "Parámetros", "/admin/seguridad", "bi-sliders",
            "Catálogos", "/admin/catalogos", "bi-book");
    }

    private final PermisoService permisos;

    public MenuService(PermisoService permisos) {
        this.permisos = permisos;
    }

    public List<Grupo> menu(Usuario u) {
        Set<String> mods = permisos.modulos(u);
        List<Grupo> r = new ArrayList<>();
        for (Grupo g : GRUPOS.values()) {
            if (mods.contains(g.modulo.name())) {
                r.add(g);
            }
        }
        return r;
    }

    public String inicio(Usuario u) {
        List<Grupo> m = menu(u);
        return m.isEmpty() ? "/" : m.get(0).items.get(0).ruta;
    }

    private static final String[][] BUSQUEDAS = {
        {"HCE", "/hce/buscar", "Buscar paciente o historia clínica…"},
        {"ADMISIONES", "/admisiones", "Buscar paciente por documento o nombre…"},
        {"ESPECIALIDADES", "/especialista/consentimientos", "Buscar paciente…"},
        {"FARMACIA", "/farmacia/inventario", "Buscar medicamento o CUM…"},
        {"FACTURACION", "/facturacion/facturas", "Buscar factura, paciente o EPS…"},
        {"SEGURIDAD", "/admin/usuarios", "Buscar usuario…"},
    };

    /** Ruta y texto del buscador de la barra superior según el primer módulo del rol que admite búsqueda. */
    public String[] busqueda(Usuario u) {
        Set<String> mods = permisos.modulos(u);
        for (String[] b : BUSQUEDAS) {
            if (mods.contains(b[0])) {
                return new String[] {b[1], b[2]};
            }
        }
        return null;
    }
}
