package co.santahelena.his.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import co.santahelena.his.model.Alerta;
import co.santahelena.his.model.Usuario;

@Service
public class AlertaService {
    private final MongoTemplate mongo;

    public AlertaService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Alerta crear(String rol, String tipo, String severidad, String pacienteId, String pacienteNombre, String mensaje, String enlace) {
        Alerta a = new Alerta();
        a.rolDestino = rol;
        a.tipo = tipo;
        a.severidad = severidad;
        a.pacienteId = pacienteId;
        a.pacienteNombre = pacienteNombre;
        a.mensaje = mensaje;
        a.enlace = enlace;
        return mongo.insert(a);
    }

    private Criteria deUsuario(Usuario u) {
        return Criteria.where("rolDestino").in(u.roles);
    }

    public long contarNoLeidas(Usuario u) {
        return mongo.count(Query.query(deUsuario(u).and("leida").is(false)), Alerta.class);
    }

    public List<Alerta> recientes(Usuario u, int limite) {
        Query q = Query.query(deUsuario(u)).with(Sort.by(Sort.Direction.DESC, "fecha")).limit(limite);
        return mongo.find(q, Alerta.class);
    }

    public void marcarLeida(String id, Usuario u) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("rolDestino").in(u.roles)),
            new Update().set("leida", true).set("leidaPor", u.username).set("leidaEn", LocalDateTime.now()), Alerta.class);
    }

    public void marcarTodas(Usuario u) {
        mongo.updateMulti(Query.query(deUsuario(u).and("leida").is(false)),
            new Update().set("leida", true).set("leidaPor", u.username).set("leidaEn", LocalDateTime.now()), Alerta.class);
    }
}
