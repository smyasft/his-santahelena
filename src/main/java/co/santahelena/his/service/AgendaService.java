package co.santahelena.his.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import co.santahelena.his.model.Agenda;
import co.santahelena.his.model.Cita;
import co.santahelena.his.model.Paciente;
import co.santahelena.his.repo.AgendaRepo;
import co.santahelena.his.repo.CitaRepo;
import co.santahelena.his.util.ReglaException;
import co.santahelena.his.util.Reglas;

@Service
public class AgendaService {
    private final AgendaRepo agendas;
    private final CitaRepo citas;
    private final PacienteService pacientes;
    private final AuditoriaService auditoria;
    private final SesionService sesion;

    public AgendaService(AgendaRepo agendas, CitaRepo citas, PacienteService pacientes, AuditoriaService auditoria, SesionService sesion) {
        this.agendas = agendas;
        this.citas = citas;
        this.pacientes = pacientes;
        this.auditoria = auditoria;
        this.sesion = sesion;
    }

    public Agenda agenda(String id) {
        return agendas.findById(id).orElseThrow(() -> new ReglaException("La agenda no existe."));
    }

    private void bloque(List<LocalDateTime> r, LocalDate fecha, String ini, String fin, int dur) {
        if (ini == null || fin == null || ini.isBlank() || fin.isBlank()) {
            return;
        }
        LocalTime t = LocalTime.parse(ini);
        LocalTime f = LocalTime.parse(fin);
        while (!t.plusMinutes(dur).isAfter(f)) {
            r.add(fecha.atTime(t));
            t = t.plusMinutes(dur);
        }
    }

    /** Franjas libres de una agenda para una fecha (excluye ocupadas y horas pasadas). */
    public List<LocalDateTime> franjasLibres(Agenda a, LocalDate fecha) {
        List<LocalDateTime> r = new ArrayList<>();
        if (fecha == null || !a.dias.contains(fecha.getDayOfWeek().getValue())) {
            return r;
        }
        bloque(r, fecha, a.mananaInicio, a.mananaFin, a.duracionMinutos);
        bloque(r, fecha, a.tardeInicio, a.tardeFin, a.duracionMinutos);
        Set<LocalDateTime> ocupadas = new HashSet<>();
        for (Cita c : citas.findByProfesionalIdAndFechaHoraBetween(a.profesionalId, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())) {
            if (!"CANCELADA".equals(c.estado)) {
                ocupadas.add(c.fechaHora);
            }
        }
        LocalDateTime ahora = LocalDateTime.now();
        r.removeIf(t -> ocupadas.contains(t) || t.isBefore(ahora));
        return r;
    }

    public Cita asignar(String pacienteId, String agendaId, LocalDateTime fechaHora, String canal, String tipo) {
        Paciente p = pacientes.obtener(pacienteId);
        Agenda a = agenda(agendaId);
        if (fechaHora == null) {
            throw new ReglaException("Seleccione la franja horaria.");
        }
        if (!franjasLibres(a, fechaHora.toLocalDate()).contains(fechaHora)) {
            throw new ReglaException("La franja seleccionada ya no está disponible. Elija otra.");
        }
        for (Cita c : citas.findByPacienteIdAndFechaHoraBetween(p.id, fechaHora.minusMinutes(1), fechaHora.plusMinutes(a.duracionMinutos))) {
            if (!"CANCELADA".equals(c.estado)) {
                throw new ReglaException("El paciente ya tiene otra cita a esa hora con " + c.profesionalNombre + ".");
            }
        }
        Cita c = new Cita();
        c.pacienteId = p.id;
        c.pacienteNombre = p.nombreCompleto();
        c.pacienteDocumento = p.documentoTexto();
        c.profesionalId = a.profesionalId;
        c.profesionalNombre = a.profesionalNombre;
        c.especialidad = a.especialidad;
        c.sede = a.sede;
        c.consultorio = a.consultorio;
        c.fechaHora = fechaHora;
        c.canal = canal;
        c.tipo = tipo == null ? "Primera vez" : tipo;
        c.creadaPor = sesion.nombre();
        c = citas.save(c);
        auditoria.registrar("CITA_ASIGNADA", canal, p, a.profesionalNombre + " " + fechaHora);
        return c;
    }

    public Cita cita(String id) {
        return citas.findById(id).orElseThrow(() -> new ReglaException("La cita no existe."));
    }

    public void cancelar(String citaId, String motivo, String pacienteIdPortal) {
        Cita c = cita(citaId);
        if (pacienteIdPortal != null) {
            if (!pacienteIdPortal.equals(c.pacienteId)) {
                throw new ReglaException("La cita no pertenece a su usuario.");
            }
            if (!Reglas.puedeCancelarPorPortal(c.fechaHora, LocalDateTime.now())) {
                throw new ReglaException("Por el portal solo puede cancelar con mínimo 24 horas de anticipación. Comuníquese con la clínica.");
            }
        }
        if (!"ASIGNADA".equals(c.estado)) {
            throw new ReglaException("Solo se pueden cancelar citas asignadas.");
        }
        c.estado = "CANCELADA";
        c.canceladaEn = LocalDateTime.now();
        c.motivoCancelacion = motivo == null ? "Sin motivo" : motivo;
        citas.save(c);
        auditoria.registrar("CITA_CANCELADA", pacienteIdPortal != null ? "PORTAL" : "VENTANILLA", c.pacienteId, c.pacienteNombre, c.motivoCancelacion);
    }

    public Cita reprogramar(String citaId, LocalDateTime nueva, String pacienteIdPortal) {
        Cita c = cita(citaId);
        Agenda a = agendas.findAll().stream().filter(x -> x.profesionalId.equals(c.profesionalId)).findFirst()
            .orElseThrow(() -> new ReglaException("El profesional no tiene agenda activa."));
        cancelar(citaId, "Reprogramada", pacienteIdPortal);
        return asignar(c.pacienteId, a.id, nueva, pacienteIdPortal != null ? "PORTAL" : "VENTANILLA", c.tipo);
    }
}
