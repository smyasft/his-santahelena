package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Movimiento;

public interface MovimientoRepo extends MongoRepository<Movimiento, String> {
    List<Movimiento> findByMedicamentoIdOrderByFechaDesc(String medicamentoId);
    List<Movimiento> findByControlEspecialTrueOrderByFechaDesc();
}
