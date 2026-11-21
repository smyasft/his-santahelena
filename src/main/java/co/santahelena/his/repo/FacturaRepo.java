package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Factura;

public interface FacturaRepo extends MongoRepository<Factura, String> {
    List<Factura> findAllByOrderByFechaDesc();
    List<Factura> findByPacienteIdOrderByFechaDesc(String pacienteId);
    List<Factura> findByFechaBetweenOrderByFechaAsc(LocalDateTime desde, LocalDateTime hasta);
}
