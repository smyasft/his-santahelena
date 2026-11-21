package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Cama;

public interface CamaRepo extends MongoRepository<Cama, String> {
    Optional<Cama> findByCodigo(String codigo);
    List<Cama> findAllByOrderByServicioAscCodigoAsc();
    List<Cama> findByEstado(String estado);
}
