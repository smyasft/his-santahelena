package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Triaje;

public interface TriajeRepo extends MongoRepository<Triaje, String> {
    List<Triaje> findByAdmisionIdOrderByFechaDesc(String admisionId);
    List<Triaje> findByPacienteIdOrderByFechaDesc(String pacienteId);
}
