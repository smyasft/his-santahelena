package co.santahelena.his.model;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Parámetros generales: MFA por rol y rangos de alerta de signos vitales. */
@Document(collection = "configuracion")
public class Configuracion {
    @Id
    public String id = "GENERAL";
    public String razonSocial = "Clínica Santa Helena IPS";
    public String nit = "900.000.123-4";
    public String codigoHabilitacion = "680010000101";
    public String ciudad = "Bucaramanga, Santander";
    public Set<String> rolesConMfa = new LinkedHashSet<>();

    public int taSistolicaMin = 90;
    public int taSistolicaMax = 140;
    public int taDiastolicaMin = 60;
    public int taDiastolicaMax = 90;
    public int fcMin = 60;
    public int fcMax = 100;
    public int frMin = 12;
    public int frMax = 20;
    public double temperaturaMin = 36.0;
    public double temperaturaMax = 37.8;
    public int saturacionMin = 92;
    public int glucometriaMin = 70;
    public int glucometriaMax = 180;
}
