package co.santahelena.his.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import co.santahelena.his.model.Paciente;
import co.santahelena.his.repo.PacienteRepo;

/**
 * SIMULACIÓN de la consulta de derechos ante ADRES/BDUA.
 * Regla de prueba: último dígito 0-7 ACTIVO, 8 SUSPENDIDO, 9 RETIRADO.
 */
@Service
public class AdresService {
    private final PacienteRepo pacientes;
    private final AuditoriaService auditoria;

    public AdresService(PacienteRepo pacientes, AuditoriaService auditoria) {
        this.pacientes = pacientes;
        this.auditoria = auditoria;
    }

    public String consultar(Paciente p) {
        String estado;
        if (p.noIdentificado) {
            estado = "NO VERIFICABLE";
        } else {
            char u = p.numeroDocumento.charAt(p.numeroDocumento.length() - 1);
            estado = u == '9' ? "RETIRADO" : (u == '8' ? "SUSPENDIDO" : "ACTIVO");
        }
        p.derechosEstado = estado;
        p.derechosFecha = LocalDateTime.now();
        pacientes.save(p);
        auditoria.registrar("CONSULTA_DERECHOS", "ADRES (simulado)", p, estado);
        return estado;
    }
}
