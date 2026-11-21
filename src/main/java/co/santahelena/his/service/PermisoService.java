package co.santahelena.his.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import co.santahelena.his.model.Modulo;
import co.santahelena.his.model.PermisoRol;
import co.santahelena.his.model.Rol;
import co.santahelena.his.model.Usuario;
import co.santahelena.his.repo.PermisoRepo;

/** Control de acceso por rol (RBAC) configurable desde el módulo de seguridad (US-002). */
@Service
public class PermisoService {
    public static final Map<String, Set<String>> PREDETERMINADOS = new LinkedHashMap<>();

    static {
        PREDETERMINADOS.put("ADMIN", Set.of("SEGURIDAD"));
        PREDETERMINADOS.put("RECEPCION", Set.of("ADMISIONES"));
        PREDETERMINADOS.put("PACIENTE", Set.of("PORTAL"));
        PREDETERMINADOS.put("ENFERMERIA", Set.of("ENFERMERIA"));
        PREDETERMINADOS.put("MEDICO", Set.of("HCE"));
        PREDETERMINADOS.put("ESPECIALISTA", Set.of("HCE", "ESPECIALIDADES"));
        PREDETERMINADOS.put("LABORATORIO", Set.of("LABORATORIO"));
        PREDETERMINADOS.put("FARMACIA", Set.of("FARMACIA"));
        PREDETERMINADOS.put("FACTURACION", Set.of("FACTURACION"));
        PREDETERMINADOS.put("CAMAS", Set.of("CAMAS"));
    }

    private final PermisoRepo repo;

    public PermisoService(PermisoRepo repo) {
        this.repo = repo;
    }

    public void inicializar() {
        for (Map.Entry<String, Set<String>> e : PREDETERMINADOS.entrySet()) {
            if (!repo.existsById(e.getKey())) {
                repo.save(PermisoRol.de(e.getKey(), e.getValue()));
            }
        }
    }

    public Map<String, Set<String>> matriz() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        for (Rol r : Rol.values()) {
            m.put(r.name(), repo.findById(r.name()).map(p -> p.modulos).orElse(new LinkedHashSet<>()));
        }
        return m;
    }

    public void guardar(Map<String, Set<String>> matriz) {
        for (Map.Entry<String, Set<String>> e : matriz.entrySet()) {
            repo.save(PermisoRol.de(e.getKey(), e.getValue()));
        }
    }

    public Set<String> modulos(Usuario u) {
        Set<String> r = new LinkedHashSet<>();
        if (u == null) {
            return r;
        }
        for (String rol : u.roles) {
            repo.findById(rol).ifPresent(p -> r.addAll(p.modulos));
        }
        return r;
    }

    public boolean permitido(Usuario u, Modulo m) {
        return modulos(u).contains(m.name());
    }
}
