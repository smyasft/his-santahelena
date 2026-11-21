package co.santahelena.his.model;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Módulos habilitados para cada rol (matriz RBAC editable por el administrador). */
@Document(collection = "permisos")
public class PermisoRol {
    @Id
    public String rol;
    public Set<String> modulos = new LinkedHashSet<>();

    public static PermisoRol de(String rol, Set<String> modulos) {
        PermisoRol p = new PermisoRol();
        p.rol = rol;
        p.modulos = new LinkedHashSet<>(modulos);
        return p;
    }
}
