package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Administracion;

public interface AdministracionRepo extends MongoRepository<Administracion, String> {
    List<Administracion> findByPacienteIdOrderByFechaDesc(String pacienteId);
}
