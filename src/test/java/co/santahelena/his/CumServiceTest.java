package co.santahelena.his;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import co.santahelena.his.service.CumService;

/** Pruebas del procesamiento de la respuesta de datos.gov.co (no hacen llamadas reales). */
class CumServiceTest {

    private static Map<String, Object> fila(String exp, String con, String producto, String atc, String principio, String cant) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expediente", exp);
        m.put("consecutivocum", con);
        m.put("producto", producto);
        m.put("formafarmaceutica", "TABLETA");
        m.put("viaadministracion", "ORAL");
        m.put("atc", atc);
        m.put("principioactivo", principio);
        m.put("cantidad", cant);
        m.put("unidadmedida", "mg");
        return m;
    }

    @Test
    void agrupaFilasPorCum() {
        List<CumService.Resultado> r = CumService.agrupar(List.of(
            fila("20078742", "37", "NORAVER NOCHE", "N02BE75", "ACETAMINOFEN", "500"),
            fila("20078742", "37", "NORAVER NOCHE", "N02BE75", "CLORHIDRATO DE DIFENHIDRAMINA", "25"),
            fila("20088116", "2", "X RAY DOL", "N02BE51", "ACETAMINOFEN", "250")));
        assertEquals(2, r.size());
        assertEquals("20078742-37", r.get(0).cum);
        assertEquals(2, r.get(0).principios.size());
        assertTrue(r.get(0).principiosTexto().contains("ACETAMINOFEN 500 mg"));
    }

    @Test
    void sugiereGrupoYControlPorAtc() {
        assertEquals("ANALGESICO", CumService.grupoPorAtc("N02BE01"));
        assertEquals("AINE", CumService.grupoPorAtc("M01AE01"));
        assertEquals("PENICILINA", CumService.grupoPorAtc("J01CA04"));
        assertEquals("ARA-II", CumService.grupoPorAtc("C09CA01"));
        assertEquals("OPIOIDE", CumService.grupoPorAtc("N02AA01"));
        assertEquals("OTRO", CumService.grupoPorAtc("Z99"));
        assertTrue(CumService.controlPorAtc("N02AA01"));
        assertTrue(CumService.controlPorAtc("N03AE01"));
        assertFalse(CumService.controlPorAtc("N02BE01"));
    }
}
