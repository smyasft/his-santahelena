package co.santahelena.his.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Entidad pagadora con convenio. El factor multiplica la tarifa base (RN-12). */
@Document(collection = "eps")
public class Eps {
    @Id
    public String id;
    @Indexed(unique = true)
    public String codigo;
    public String nombre;
    public String nit;
    public String tipo;
    public double factorTarifa = 1.0;
    public boolean activo = true;
}
