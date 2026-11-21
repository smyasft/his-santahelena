package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Agenda;

public interface AgendaRepo extends MongoRepository<Agenda, String> {
    List<Agenda> findAllByOrderByProfesionalNombreAsc();
}
