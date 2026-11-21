package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Eps;

public interface EpsRepo extends MongoRepository<Eps, String> {
    Optional<Eps> findByCodigo(String codigo);
    List<Eps> findAllByOrderByNombreAsc();
    List<Eps> findByActivoTrueOrderByNombreAsc();
}
