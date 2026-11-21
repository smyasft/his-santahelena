package co.santahelena.his.repo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.santahelena.his.model.Usuario;

public interface UsuarioRepo extends MongoRepository<Usuario, String> {
    Optional<Usuario> findByUsername(String username);
    Optional<Usuario> findByDocumento(String documento);
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByPacienteId(String pacienteId);
    List<Usuario> findAllByOrderByNombreAsc();
}
