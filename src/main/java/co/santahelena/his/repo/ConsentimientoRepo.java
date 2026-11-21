package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Consentimiento;

public interface ConsentimientoRepo extends MongoRepository<Consentimiento, String> {
    List<Consentimiento> findAllByOrderByFechaDesc();
    List<Consentimiento> findByPacienteIdOrderByFechaDesc(String pacienteId);
}
