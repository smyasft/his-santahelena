package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.ExamenLab;

public interface ExamenLabRepo extends MongoRepository<ExamenLab, String> {
    Optional<ExamenLab> findByCodigo(String codigo);
    List<ExamenLab> findAllByOrderByNombreAsc();
    List<ExamenLab> findByActivoTrueOrderByNombreAsc();
}
