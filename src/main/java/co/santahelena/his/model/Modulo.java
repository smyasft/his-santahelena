package co.santahelena.his.model;

/** Módulos del sistema; cada uno corresponde a una ruta base y se habilita por rol (RBAC). */
public enum Modulo {
    SEGURIDAD("Seguridad y Configuración", "/admin"),
    ADMISIONES("Admisiones y Citas", "/admisiones"),
    PORTAL("Portal del Paciente", "/portal"),
    ENFERMERIA("Triaje y Enfermería", "/enfermeria"),
    HCE("Historia Clínica Electrónica", "/hce"),
    ESPECIALIDADES("Especialidades y Cirugía", "/especialista"),
    LABORATORIO("Laboratorio Clínico", "/laboratorio"),
    FARMACIA("Farmacia e Inventario", "/farmacia"),
    FACTURACION("Facturación y Convenios", "/facturacion"),
    CAMAS("Hospitalización y Camas", "/camas");

    public final String nombre;
    public final String ruta;

    Modulo(String nombre, String ruta) {
        this.nombre = nombre;
        this.ruta = ruta;
    }

    public static Modulo deRuta(String uri) {
        for (Modulo m : values()) {
            if (uri.equals(m.ruta) || uri.startsWith(m.ruta + "/")) {
                return m;
            }
        }
        return null;
    }
}
