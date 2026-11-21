package co.santahelena.his.service;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import co.santahelena.his.model.Consecutivo;

/** Consecutivos atómicos (historias, facturas, muestras…) con findAndModify. */
@Service
public class ConsecutivoService {
    private final MongoTemplate mongo;

    public ConsecutivoService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public long siguiente(String nombre) {
        Consecutivo c = mongo.findAndModify(
            Query.query(Criteria.where("_id").is(nombre)),
            new Update().inc("valor", 1),
            FindAndModifyOptions.options().returnNew(true).upsert(true),
            Consecutivo.class);
        return c == null ? 1 : c.valor;
    }

    public String formato(String nombre, String prefijo, int digitos) {
        return prefijo + String.format("%0" + digitos + "d", siguiente(nombre));
    }
}
