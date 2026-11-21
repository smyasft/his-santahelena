package co.santahelena.his.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import co.santahelena.his.model.Auditoria;
import co.santahelena.his.model.Paciente;
import co.santahelena.his.model.Usuario;

/** Registro de auditoría de solo inserción: no existe ningún método de actualización ni borrado. */
@Service
public class AuditoriaService {
    private final MongoTemplate mongo;
    private final SesionService sesion;

    public AuditoriaService(MongoTemplate mongo, SesionService sesion) {
        this.mongo = mongo;
        this.sesion = sesion;
    }

    public void registrar(String accion, String recurso, Paciente p, String detalle) {
        registrar(accion, recurso, p == null ? null : p.id, p == null ? null : p.nombreCompleto(), detalle);
    }

    public void registrar(String accion, String recurso, String pacienteId, String pacienteNombre, String detalle) {
        Usuario u = sesion.usuario();
        Auditoria a = base(u == null ? "anónimo" : u.username, u == null ? "" : String.join(",", u.roles), accion, recurso, detalle);
        a.pacienteId = pacienteId;
        a.pacienteNombre = pacienteNombre;
        mongo.insert(a);
    }

    public void registrarComo(String usuario, String roles, String accion, String recurso, String detalle) {
        mongo.insert(base(usuario, roles, accion, recurso, detalle));
    }

    private Auditoria base(String usuario, String roles, String accion, String recurso, String detalle) {
        Auditoria a = new Auditoria();
        a.fecha = LocalDateTime.now();
        a.usuario = usuario;
        a.roles = roles;
        a.accion = accion;
        a.recurso = recurso;
        a.detalle = detalle;
        a.ip = ip();
        return a;
    }

    private String ip() {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra instanceof ServletRequestAttributes s) {
            String f = s.getRequest().getHeader("X-Forwarded-For");
            return f != null && !f.isBlank() ? f.split(",")[0].trim() : s.getRequest().getRemoteAddr();
        }
        return "-";
    }

    public List<Auditoria> buscar(String usuario, String accion, String paciente, LocalDate desde, LocalDate hasta, int limite) {
        Query q = new Query();
        if (usuario != null) {
            q.addCriteria(Criteria.where("usuario").regex(Pattern.quote(usuario), "i"));
        }
        if (accion != null) {
            q.addCriteria(Criteria.where("accion").is(accion));
        }
        if (paciente != null) {
            q.addCriteria(Criteria.where("pacienteNombre").regex(Pattern.quote(paciente), "i"));
        }
        if (desde != null || hasta != null) {
            Criteria c = Criteria.where("fecha");
            if (desde != null) {
                c = c.gte(desde.atStartOfDay());
            }
            if (hasta != null) {
                c = c.lt(hasta.plusDays(1).atStartOfDay());
            }
            q.addCriteria(c);
        }
        q.with(Sort.by(Sort.Direction.DESC, "fecha")).limit(limite);
        return mongo.find(q, Auditoria.class);
    }

    public List<String> acciones() {
        return mongo.findDistinct(new Query(), "accion", Auditoria.class, String.class);
    }
}
