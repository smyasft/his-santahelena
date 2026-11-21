package co.santahelena.his.model;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "usuarios")
public class Usuario {
    @Id
    public String id;
    @Indexed(unique = true)
    public String username;
    public String nombre;
    @Indexed(unique = true, sparse = true)
    public String documento;
    @Indexed(unique = true, sparse = true)
    public String email;
    public Set<String> roles = new LinkedHashSet<>();
    public boolean activo = true;
    public String passwordHash;
    public String mfaSecreto;
    public boolean mfaConfigurado;
    public int intentosFallidos;
    public LocalDateTime bloqueadoHasta;
    public String especialidad;
    public String registroMedico;
    public String pacienteId;
    public LocalDateTime creadoEn = LocalDateTime.now();
    public LocalDateTime ultimoAcceso;

    public boolean bloqueado() {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(LocalDateTime.now());
    }

    public boolean tieneRol(String rol) {
        return roles.contains(rol);
    }

    public String rolesTexto() {
        return String.join(", ", roles.stream().map(Rol::nombreDe).toList());
    }

    public String iniciales() {
        if (nombre == null || nombre.isBlank()) {
            return "?";
        }
        String[] p = nombre.replace("Dr. ", "").replace("Dra. ", "").replace("Bact. ", "").replace("Q.F. ", "").trim().split("\\s+");
        return (p[0].substring(0, 1) + (p.length > 1 ? p[p.length > 2 ? 2 : 1].substring(0, 1) : "")).toUpperCase();
    }
}
