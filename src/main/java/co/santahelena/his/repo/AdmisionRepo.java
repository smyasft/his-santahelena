package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Admision;

public interface AdmisionRepo extends MongoRepository<Admision, String> {
    List<Admision> findByTipoAndEstadoInOrderByFechaIngresoAsc(String tipo, Collection<String> estados);
    List<Admision> findByPacienteIdOrderByFechaIngresoDesc(String pacienteId);
    List<Admision> findByEstadoOrderByFechaIngresoAsc(String estado);
    List<Admision> findByTipoOrderByFechaIngresoDesc(String tipo);
}
