package co.santahelena.his.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "lotes")
@CompoundIndex(name = "medicamento_lote", def = "{'medicamentoId': 1, 'numero': 1}", unique = true)
public class Lote {
    @Id
    public String id;
    public String medicamentoId;
    public String medicamentoNombre;
    public String numero;
    public LocalDate vence;
    public int cantidad;
    public String bodega = "Farmacia central";
    public String proveedor;
    public LocalDateTime fechaIngreso = LocalDateTime.now();

    public boolean vencido() {
        return vence != null && vence.isBefore(LocalDate.now());
    }

    public boolean porVencer() {
        return vence != null && !vencido() && vence.isBefore(LocalDate.now().plusDays(90));
    }
}
