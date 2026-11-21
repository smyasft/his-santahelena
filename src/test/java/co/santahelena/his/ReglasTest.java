package co.santahelena.his;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.santahelena.his.model.Cama;
import co.santahelena.his.model.Configuracion;
import co.santahelena.his.model.Lote;
import co.santahelena.his.model.Medicamento;
import co.santahelena.his.model.SignosVitales;
import co.santahelena.his.util.Hash;
import co.santahelena.his.util.Reglas;
import co.santahelena.his.util.Totp;

/** Pruebas unitarias de las reglas de negocio (no requieren MongoDB). Ejecutar: mvn test */
class ReglasTest {

    @Test
    void totpCumpleVectorRfc6238() {
        String secreto = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertEquals("287082", Totp.codigo(secreto, 1));
        assertEquals("081804", Totp.codigo(secreto, 37037036L));
    }

    @Test
    void fefoDescuentaPrimeroElLoteMasProximoAVencer() {
        LocalDate hoy = LocalDate.of(2027, 2, 1);
        Lote vencido = lote("A", hoy.minusDays(1), 50);
        Lote tarde = lote("B", hoy.plusMonths(6), 10);
        Lote pronto = lote("C", hoy.plusMonths(2), 5);
        List<Reglas.Asignacion> r = Reglas.fefo(List.of(vencido, tarde, pronto), 8, hoy);
        assertNotNull(r);
        assertEquals("C", r.get(0).lote.numero);
        assertEquals(5, r.get(0).cantidad);
        assertEquals(3, r.get(1).cantidad);
        assertNull(Reglas.fefo(List.of(vencido, tarde, pronto), 16, hoy));
        assertEquals(15, Reglas.stockVigente(List.of(vencido, tarde, pronto), hoy));
    }

    @Test
    void triajeSugiereNivelSegunSignos() {
        SignosVitales s = new SignosVitales();
        s.taSistolica = 120;
        s.taDiastolica = 80;
        s.fc = 80;
        s.fr = 16;
        s.temperatura = 36.8;
        s.saturacion = 98;
        assertEquals(5, Reglas.sugerirNivel(s));
        s.saturacion = 82;
        assertEquals(1, Reglas.sugerirNivel(s));
        s.saturacion = 98;
        s.fc = 400;
        assertNotNull(Reglas.valorImposible(s));
        s.fc = 80;
        s.taSistolica = 160;
        assertFalse(Reglas.alertasSignos(s, new Configuracion()).isEmpty());
    }

    @Test
    void camaRespetaSexoAislamientoYServicio() {
        Cama c = new Cama();
        c.estado = "LIBRE";
        c.sexoPermitido = "F";
        c.servicio = "Medicina Interna";
        assertNotNull(Reglas.incompatibilidadCama(c, "M", false, "Medicina Interna"));
        c.sexoPermitido = null;
        assertNotNull(Reglas.incompatibilidadCama(c, "M", true, "Medicina Interna"));
        c.aislamiento = true;
        assertNull(Reglas.incompatibilidadCama(c, "M", true, "Medicina Interna"));
        assertNotNull(Reglas.incompatibilidadCama(c, "M", true, "Cirugía"));
    }

    @Test
    void prescripcionDetectaAlergiaInteraccionYDuplicidad() {
        Medicamento amox = med("1", "Amoxicilina 500 mg", "PENICILINA");
        Medicamento enalapril = med("2", "Enalapril 20 mg", "IECA");
        Medicamento losartan = med("3", "Losartán 50 mg", "ARA-II");
        List<String> al = Reglas.alertasPrescripcion(List.of(amox, losartan), List.of(enalapril), List.of("Penicilina"));
        assertTrue(al.stream().anyMatch(x -> x.startsWith("ALERGIA")));
        assertTrue(al.stream().anyMatch(x -> x.startsWith("INTERACC")));
        assertTrue(Reglas.alertasPrescripcion(List.of(enalapril), List.of(enalapril), List.of()).stream().anyMatch(x -> x.startsWith("DUPLICIDAD")));
    }

    @Test
    void politicasVarias() {
        assertNull(Reglas.politicaPassword("Clinica2026*"));
        assertNotNull(Reglas.politicaPassword("clinica"));
        LocalDateTime ahora = LocalDateTime.now();
        assertTrue(Reglas.puedeCancelarPorPortal(ahora.plusHours(30), ahora));
        assertFalse(Reglas.puedeCancelarPorPortal(ahora.plusHours(5), ahora));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hash.sha256("abc"));
    }

    private static Lote lote(String numero, LocalDate vence, int cantidad) {
        Lote l = new Lote();
        l.numero = numero;
        l.vence = vence;
        l.cantidad = cantidad;
        return l;
    }

    private static Medicamento med(String id, String nombre, String grupo) {
        Medicamento m = new Medicamento();
        m.id = id;
        m.nombre = nombre;
        m.grupo = grupo;
        return m;
    }
}
