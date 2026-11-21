package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Medicamento;

public interface MedicamentoRepo extends MongoRepository<Medicamento, String> {
    Optional<Medicamento> findByCodigoCum(String codigoCum);
    List<Medicamento> findAllByOrderByNombreAsc();
    List<Medicamento> findByActivoTrueOrderByNombreAsc();
}
