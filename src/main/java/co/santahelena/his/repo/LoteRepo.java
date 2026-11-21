package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Lote;

public interface LoteRepo extends MongoRepository<Lote, String> {
    List<Lote> findByMedicamentoId(String medicamentoId);
    List<Lote> findAllByOrderByVenceAsc();
}
