package co.santahelena.his.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import co.santahelena.his.model.Usuario;
import co.santahelena.his.repo.UsuarioRepo;

@Service
public class HisUserDetailsService implements UserDetailsService {
    private final UsuarioRepo usuarios;

    public HisUserDetailsService(UsuarioRepo usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario u = usuarios.findByUsername(username == null ? "" : username.trim().toLowerCase())
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return User.withUsername(u.username)
            .password(u.passwordHash)
            .authorities(u.roles.stream().map(r -> "ROLE_" + r).toArray(String[]::new))
            .accountLocked(u.bloqueado())
            .disabled(!u.activo)
            .build();
    }
}
