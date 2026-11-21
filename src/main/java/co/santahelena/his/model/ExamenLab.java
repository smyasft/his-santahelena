package co.santahelena.his.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Catálogo de exámenes de laboratorio con sus analitos y rangos (US-035). */
@Document(collection = "examenes_lab")
public class ExamenLab {
    @Id
    public String id;
    @Indexed(unique = true)
    public String codigo;
    public String nombre;
    public String muestra = "Sangre";
    public double precio;
    public boolean activo = true;
    public List<Analito> analitos = new ArrayList<>();

    public static class Analito {
        public String nombre;
        public String unidad;
        public Double min;
        public Double max;
        public Double criticoMin;
        public Double criticoMax;

        public String rango() {
            if (min == null && max == null) {
                return "—";
            }
            if (min == null) {
                return "< " + max;
            }
            if (max == null) {
                return "> " + min;
            }
            return min + " – " + max;
        }
    }
}
