package co.santahelena.his.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "consecutivos")
public class Consecutivo {
    @Id
    public String id;
    public long valor;
}
