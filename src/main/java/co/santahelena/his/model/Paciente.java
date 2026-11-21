package co.santahelena.his.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import co.santahelena.his.util.Form;

/** Un documento por persona: el índice único impide historias duplicadas (RN-03). */
@Document(collection = "pacientes")
@CompoundIndex(name = "documento_unico", def = "{'tipoDocumento': 1, 'numeroDocumento': 1}", unique = true)
public class Paciente {
    @Id
    public String id;
    @Indexed(unique = true)
    public String numeroHistoria;
    public String tipoDocumento;
    @Indexed
    public String numeroDocumento;
    public String nombres;
    public String apellidos;
    public LocalDate fechaNacimiento;
    public String sexo;
    public String epsId;
    public String epsNombre;
    public String regimen;
    public String nivel;
    public String telefono;
    public String email;
    public String direccion;
    public String municipio;
    public List<String> alergias = new ArrayList<>();
    public boolean noIdentificado;
    public LocalDateTime consentimientoDatos;
    public String acompananteNombre;
    public String acompananteTelefono;
    public String acompananteParentesco;
    public String derechosEstado;
    public LocalDateTime derechosFecha;
    public String nombreBusqueda;
    public LocalDateTime creadoEn = LocalDateTime.now();
    public String creadoPor;

    public String nombreCompleto() {
        return ((nombres == null ? "" : nombres) + " " + (apellidos == null ? "" : apellidos)).trim();
    }

    public String documentoTexto() {
        return tipoDocumento + " " + numeroDocumento;
    }

    public int edad() {
        if (fechaNacimiento == null) {
            return -1;
        }
        return Period.between(fechaNacimiento, LocalDate.now()).getYears();
    }

    public String edadTexto() {
        if (fechaNacimiento == null) {
            return "Edad no registrada";
        }
        Period p = Period.between(fechaNacimiento, LocalDate.now());
        if (p.getYears() >= 2) {
            return p.getYears() + " años";
        }
        return (p.getYears() * 12 + p.getMonths()) + " meses";
    }

    public String sexoTexto() {
        if ("M".equals(sexo)) {
            return "Masculino";
        }
        if ("F".equals(sexo)) {
            return "Femenino";
        }
        return "Indeterminado";
    }

    public boolean tieneAlergias() {
        return alergias != null && !alergias.isEmpty();
    }

    public String alergiasTexto() {
        return tieneAlergias() ? String.join(", ", alergias) : "No refiere";
    }

    public void actualizarBusqueda() {
        nombreBusqueda = Form.norm(nombreCompleto() + " " + numeroDocumento + " " + numeroHistoria);
    }
}
