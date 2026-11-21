package co.santahelena.his.service;

import org.springframework.stereotype.Service;

import co.santahelena.his.model.Configuracion;
import co.santahelena.his.repo.ConfiguracionRepo;

@Service
public class ConfigService {
    private final ConfiguracionRepo repo;

    public ConfigService(ConfiguracionRepo repo) {
        this.repo = repo;
    }

    public Configuracion get() {
        return repo.findById("GENERAL").orElseGet(() -> repo.save(new Configuracion()));
    }

    public Configuracion guardar(Configuracion c) {
        c.id = "GENERAL";
        return repo.save(c);
    }
}
