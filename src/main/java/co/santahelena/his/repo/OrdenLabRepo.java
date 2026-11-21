package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.OrdenLab;

public interface OrdenLabRepo extends MongoRepository<OrdenLab, String> {
    List<OrdenLab> findByEstadoInOrderByFechaAsc(Collection<String> estados);
    List<OrdenLab> findByPacienteIdOrderByFechaDesc(String pacienteId);
    List<OrdenLab> findByEstadoOrderByValidadaEnDesc(String estado);
}
