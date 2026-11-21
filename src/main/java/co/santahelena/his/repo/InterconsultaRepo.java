package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Interconsulta;

public interface InterconsultaRepo extends MongoRepository<Interconsulta, String> {
    List<Interconsulta> findByEstadoInOrderByFechaAsc(Collection<String> estados);
    List<Interconsulta> findByPacienteIdOrderByFechaDesc(String pacienteId);
}
