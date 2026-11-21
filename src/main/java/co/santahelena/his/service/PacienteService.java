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

import co.santahelena.his.model.Paciente;
import co.santahelena.his.repo.PacienteRepo;
import co.santahelena.his.util.Form;
import co.santahelena.his.util.ReglaException;

@Service
public class PacienteService {
    private final PacienteRepo repo;
    private final MongoTemplate mongo;
    private final ConsecutivoService consecutivos;
    private final AuditoriaService auditoria;
    private final SesionService sesion;

    public PacienteService(PacienteRepo repo, MongoTemplate mongo, ConsecutivoService consecutivos, AuditoriaService auditoria, SesionService sesion) {
        this.repo = repo;
        this.mongo = mongo;
        this.consecutivos = consecutivos;
        this.auditoria = auditoria;
        this.sesion = sesion;
    }

    /** Búsqueda por documento, número de historia o nombre (sin importar tildes ni mayúsculas). */
    public List<Paciente> buscar(String texto, int limite) {
        String q = Form.norm(texto);
        if (q.isEmpty()) {
            return mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "creadoEn")).limit(limite), Paciente.class);
        }
        // Todas las palabras deben aparecer (andOperator, porque Mongo no admite repetir la clave)
        String[] palabras = q.split("\\s+");
        Criteria[] partes = new Criteria[palabras.length];
        for (int i = 0; i < palabras.length; i++) {
            partes[i] = Criteria.where("nombreBusqueda").regex(Pattern.quote(palabras[i]));
        }
        Query query = Query.query(new Criteria().andOperator(partes)).limit(limite).with(Sort.by("apellidos"));
        return mongo.find(query, Paciente.class);
    }

    public Paciente obtener(String id) {
        if (id == null) {
            throw new ReglaException("Paciente no indicado.");
        }
        return repo.findById(id).orElseThrow(() -> new ReglaException("El paciente no existe."));
    }

    public Paciente guardar(Paciente p, boolean nuevo) {
        if (p.tipoDocumento == null || p.numeroDocumento == null) {
            throw new ReglaException("Tipo y número de documento son obligatorios.");
        }
        if (p.nombres == null || p.apellidos == null) {
            throw new ReglaException("Nombres y apellidos son obligatorios.");
        }
        if (p.fechaNacimiento == null || p.fechaNacimiento.isAfter(LocalDate.now())) {
            throw new ReglaException("La fecha de nacimiento es obligatoria y no puede ser futura.");
        }
        if (p.sexo == null) {
            throw new ReglaException("Indique el sexo biológico.");
        }
        Paciente otro = repo.findByTipoDocumentoAndNumeroDocumento(p.tipoDocumento, p.numeroDocumento).orElse(null);
        if (otro != null && !otro.id.equals(p.id)) {
            throw new ReglaException("Ya existe una historia con " + p.tipoDocumento + " " + p.numeroDocumento
                + " (" + otro.numeroHistoria + " – " + otro.nombreCompleto() + "). No se permiten historias duplicadas.");
        }
        if (nuevo) {
            p.numeroHistoria = consecutivos.formato("historia", "HC-", 6);
            p.creadoPor = sesion.nombre();
            p.creadoEn = LocalDateTime.now();
        }
        p.actualizarBusqueda();
        Paciente g = repo.save(p);
        auditoria.registrar(nuevo ? "PACIENTE_CREADO" : "PACIENTE_ACTUALIZADO", "/admisiones/paciente", g, g.numeroHistoria);
        return g;
    }

    /** Paciente NN para urgencias vitales sin documento (US-005). */
    public Paciente crearNoIdentificado(String sexo, String descripcion, Integer edadAproximada) {
        Paciente p = new Paciente();
        p.noIdentificado = true;
        p.tipoDocumento = "NN";
        p.numeroDocumento = consecutivos.formato("nn", "NN-", 5);
        p.nombres = "NN";
        p.apellidos = descripcion == null ? "No identificado" : descripcion;
        p.sexo = sexo == null ? "I" : sexo;
        p.fechaNacimiento = LocalDate.now().minusYears(edadAproximada == null ? 30 : edadAproximada);
        p.derechosEstado = "NO VERIFICABLE";
        return guardar(p, true);
    }
}
