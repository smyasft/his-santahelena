package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Cita;

public interface CitaRepo extends MongoRepository<Cita, String> {
    List<Cita> findByPacienteIdOrderByFechaHoraDesc(String pacienteId);
    List<Cita> findByProfesionalIdAndFechaHoraBetween(String profesionalId, LocalDateTime desde, LocalDateTime hasta);
    List<Cita> findByPacienteIdAndFechaHoraBetween(String pacienteId, LocalDateTime desde, LocalDateTime hasta);
    List<Cita> findByFechaHoraBetweenOrderByFechaHoraAsc(LocalDateTime desde, LocalDateTime hasta);
}
