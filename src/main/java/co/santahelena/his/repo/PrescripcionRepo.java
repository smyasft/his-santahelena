package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Prescripcion;

public interface PrescripcionRepo extends MongoRepository<Prescripcion, String> {
    List<Prescripcion> findByPacienteIdOrderByFechaDesc(String pacienteId);
    List<Prescripcion> findByPacienteIdAndEstado(String pacienteId, String estado);
    List<Prescripcion> findByEstadoOrderByFechaDesc(String estado);
}
