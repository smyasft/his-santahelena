package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Atencion;

public interface AtencionRepo extends MongoRepository<Atencion, String> {
    List<Atencion> findByPacienteIdOrderByFechaDesc(String pacienteId);
    List<Atencion> findByProfesionalIdAndFirmadaFalseOrderByFechaDesc(String profesionalId);
    List<Atencion> findByAclaraA(String aclaraA);
}
