package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Cuenta;

public interface CuentaRepo extends MongoRepository<Cuenta, String> {
    Optional<Cuenta> findFirstByPacienteIdAndEstado(String pacienteId, String estado);
    List<Cuenta> findByEstadoOrderByAbiertaEnDesc(String estado);
    List<Cuenta> findByPacienteIdOrderByAbiertaEnDesc(String pacienteId);
}
