package co.santahelena.his.model;

/** Los 10 roles del HIS definidos en el SRS. */
public enum Rol {
    ADMIN("Administrador del Sistema"),
    RECEPCION("Recepcionista / Admisiones"),
    PACIENTE("Paciente"),
    ENFERMERIA("Enfermero/a"),
    MEDICO("Médico General"),
    ESPECIALISTA("Médico Especialista"),
    LABORATORIO("Laboratorista"),
    FARMACIA("Farmaceuta"),
    FACTURACION("Administrador de Caja / Facturación"),
    CAMAS("Gestor de Camas / Hospitalización");

    public final String nombre;

    Rol(String nombre) {
        this.nombre = nombre;
    }

    public static String nombreDe(String codigo) {
        for (Rol r : values()) {
            if (r.name().equals(codigo)) {
                return r.nombre;
            }
        }
        return codigo;
    }
}
