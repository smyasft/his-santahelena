package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.CatalogoItem;

public interface CatalogoRepo extends MongoRepository<CatalogoItem, String> {
    Optional<CatalogoItem> findByTipoAndCodigo(String tipo, String codigo);
    long countByTipo(String tipo);
}
