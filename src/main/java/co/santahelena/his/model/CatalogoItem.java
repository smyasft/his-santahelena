package co.santahelena.his.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Catálogos maestros: CIE-10 (diagnósticos), CUPS (procedimientos) y CUM (medicamentos). */
@Document(collection = "catalogos")
@CompoundIndex(name = "tipo_codigo", def = "{'tipo': 1, 'codigo': 1}", unique = true)
public class CatalogoItem {
    @Id
    public String id;
    public String tipo;
    public String codigo;
    public String descripcion;
    public Double valor;
    public String busqueda;

    public static CatalogoItem de(String tipo, String codigo, String descripcion, Double valor) {
        CatalogoItem c = new CatalogoItem();
        c.tipo = tipo;
        c.codigo = codigo;
        c.descripcion = descripcion;
        c.valor = valor;
        c.busqueda = co.santahelena.his.util.Form.norm(codigo + " " + descripcion);
        return c;
    }
}
