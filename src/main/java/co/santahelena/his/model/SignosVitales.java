package co.santahelena.his.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "signos_vitales")
public class SignosVitales {
    @Id
    public String id;
    @Indexed
    public String pacienteId;
    public String admisionId;
    public LocalDateTime fecha = LocalDateTime.now();
    public Integer taSistolica;
    public Integer taDiastolica;
    public Integer fc;
    public Integer fr;
    public Double temperatura;
    public Integer saturacion;
    public Integer glucometria;
    public String usuario;
    public List<String> alertas = new ArrayList<>();

    public String ta() {
        return taSistolica == null ? "—" : taSistolica + "/" + (taDiastolica == null ? "?" : taDiastolica);
    }
}
