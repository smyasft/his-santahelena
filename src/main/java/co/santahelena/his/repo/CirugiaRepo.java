package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Cirugia;

public interface CirugiaRepo extends MongoRepository<Cirugia, String> {
    List<Cirugia> findByQuirofanoAndEstado(String quirofano, String estado);
    List<Cirugia> findAllByOrderByInicioAsc();
}
