package co.santahelena.his.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import co.santahelena.his.model.Admision;
import co.santahelena.his.model.Cama;
import co.santahelena.his.repo.AdmisionRepo;
import co.santahelena.his.repo.CamaRepo;
import co.santahelena.his.repo.PazYSalvoRepo;
import co.santahelena.his.util.ReglaException;
import co.santahelena.his.util.Reglas;

@Service
public class CamaService {
    private final CamaRepo camas;
    private final AdmisionRepo admisiones;
    private final PazYSalvoRepo pazYSalvos;
    private final FacturacionService facturacion;
    private final AuditoriaService auditoria;
    private final SesionService sesion;

    public CamaService(CamaRepo camas, AdmisionRepo admisiones, PazYSalvoRepo pazYSalvos, FacturacionService facturacion,
                       AuditoriaService auditoria, SesionService sesion) {
        this.camas = camas;
        this.admisiones = admisiones;
        this.pazYSalvos = pazYSalvos;
        this.facturacion = facturacion;
        this.auditoria = auditoria;
        this.sesion = sesion;
    }

    public Cama cama(String id) {
        return camas.findById(id).orElseThrow(() -> new ReglaException("La cama no existe."));
    }

    public Admision admision(String id) {
        return admisiones.findById(id).orElseThrow(() -> new ReglaException("La admisión no existe."));
    }

    public void asignar(String admisionId, String camaId) {
        Admision a = admision(admisionId);
        if (!"PENDIENTE_CAMA".equals(a.estado)) {
            throw new ReglaException("El paciente no está pendiente de cama.");
        }
        Cama c = cama(camaId);
        String error = Reglas.incompatibilidadCama(c, a.pacienteSexo, a.requiereAislamiento, a.servicio);
        if (error != null) {
            throw new ReglaException(error);
        }
        ocupar(c, a);
        a.estado = "EN_CAMA";
        a.inicioEstancia = LocalDateTime.now();
        admisiones.save(a);
        auditoria.registrar("CAMA_ASIGNADA", "/camas", a.pacienteId, a.pacienteNombre, c.codigo);
    }

    private void ocupar(Cama c, Admision a) {
        c.estado = "OCUPADA";
        c.pacienteId = a.pacienteId;
        c.pacienteNombre = a.pacienteNombre;
        c.admisionId = a.id;
        c.desde = LocalDateTime.now();
        camas.save(c);
        a.camaId = c.id;
        a.camaCodigo = c.codigo;
    }

    private void liberar(Cama c) {
        c.estado = "LIMPIEZA";
        c.pacienteId = null;
        c.pacienteNombre = null;
        c.admisionId = null;
        c.desde = LocalDateTime.now();
        camas.save(c);
    }

    public void trasladar(String admisionId, String camaDestinoId, String motivo) {
        Admision a = admision(admisionId);
        if (!"EN_CAMA".equals(a.estado)) {
            throw new ReglaException("El paciente no está en cama.");
        }
        if (motivo == null) {
            throw new ReglaException("Indique el motivo del traslado.");
        }
        Cama destino = cama(camaDestinoId);
        String error = Reglas.incompatibilidadCama(destino, a.pacienteSexo, a.requiereAislamiento, destino.servicio);
        if (error != null) {
            throw new ReglaException(error);
        }
        Cama origen = cama(a.camaId);
        Admision.Traslado t = new Admision.Traslado();
        t.fecha = LocalDateTime.now();
        t.deCama = origen.codigo;
        t.aCama = destino.codigo;
        t.motivo = motivo;
        t.usuario = sesion.nombre();
        a.traslados.add(t);
        liberar(origen);
        ocupar(destino, a);
        a.servicio = destino.servicio;
        admisiones.save(a);
        auditoria.registrar("TRASLADO_CAMA", "/camas", a.pacienteId, a.pacienteNombre, origen.codigo + " → " + destino.codigo + ": " + motivo);
    }

    public long diasEstancia(Admision a) {
        if (a.inicioEstancia == null) {
            return 0;
        }
        long horas = Duration.between(a.inicioEstancia, LocalDateTime.now()).toHours();
        return Math.max(1, (horas + 23) / 24);
    }

    /** Carga a la cuenta los días cama (código DC-001) antes del egreso. */
    public void liquidarEstancia(String admisionId) {
        Admision a = admision(admisionId);
        if (!"EN_CAMA".equals(a.estado)) {
            throw new ReglaException("Solo se liquida la estancia de pacientes en cama.");
        }
        if (!a.ordenEgreso) {
            throw new ReglaException("El médico aún no ha dado la orden de egreso.");
        }
        long dias = diasEstancia(a);
        facturacion.cargar(a.pacienteId, "DC-001", "Día cama hospitalización " + (a.servicio == null ? "" : a.servicio),
            (int) dias, facturacion.tarifaCups("DC-001", 280000), "ESTANCIA", "EST-" + a.id);
        a.estanciaLiquidada = true;
        admisiones.save(a);
        auditoria.registrar("ESTANCIA_LIQUIDADA", "/camas", a.pacienteId, a.pacienteNombre, dias + " día(s)");
    }

    /** Egreso: exige orden médica y paz y salvo de facturación (US-049, RN-09). */
    public void egresar(String admisionId) {
        Admision a = admision(admisionId);
        if (!"EN_CAMA".equals(a.estado)) {
            throw new ReglaException("El paciente no está en cama.");
        }
        if (!a.ordenEgreso) {
            throw new ReglaException("No hay orden médica de egreso.");
        }
        if (pazYSalvos.findFirstByAdmisionId(a.id).isEmpty()) {
            throw new ReglaException("Facturación aún no ha expedido el paz y salvo de esta admisión.");
        }
        Cama c = cama(a.camaId);
        liberar(c);
        a.estado = "EGRESADO";
        a.fechaEgreso = LocalDateTime.now();
        admisiones.save(a);
        auditoria.registrar("EGRESO", "/camas", a.pacienteId, a.pacienteNombre, c.codigo);
    }

    public void cambiarEstado(String camaId, String estado, String motivo) {
        Cama c = cama(camaId);
        if ("OCUPADA".equals(c.estado)) {
            throw new ReglaException("La cama está ocupada; primero egrese o traslade al paciente.");
        }
        if ("BLOQUEADA".equals(estado) && motivo == null) {
            throw new ReglaException("Indique el motivo del bloqueo.");
        }
        c.estado = estado;
        c.motivoBloqueo = "BLOQUEADA".equals(estado) ? motivo : null;
        c.desde = LocalDateTime.now();
        camas.save(c);
        auditoria.registrar("CAMA_" + estado, "/camas", null, null, c.codigo + (motivo == null ? "" : ": " + motivo));
    }
}
