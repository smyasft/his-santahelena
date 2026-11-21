package co.santahelena.his.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import co.santahelena.his.model.*;
import co.santahelena.his.repo.*;
import co.santahelena.his.service.ConfigService;
import co.santahelena.his.service.ConsecutivoService;
import co.santahelena.his.service.FacturacionService;
import co.santahelena.his.service.PermisoService;
import co.santahelena.his.util.Hash;

/**
 * Carga datos de demostración la primera vez que arranca (base vacía).
 * Se desactiva con his.datos-demo=false en application.properties.
 */
@Component
public class DatosDemo implements CommandLineRunner {
    public static final String CLAVE_DEMO = "Clinica2026*";

    @Value("${his.datos-demo:true}")
    private boolean activo;

    private final UsuarioRepo usuarios;
    private final EpsRepo eps;
    private final CatalogoRepo catalogos;
    private final MedicamentoRepo medicamentos;
    private final LoteRepo lotes;
    private final MovimientoRepo movimientos;
    private final ExamenLabRepo examenes;
    private final CamaRepo camas;
    private final PacienteRepo pacientes;
    private final AgendaRepo agendas;
    private final CitaRepo citas;
    private final AdmisionRepo admisiones;
    private final SignosRepo signos;
    private final TriajeRepo triajes;
    private final AtencionRepo atenciones;
    private final PrescripcionRepo prescripciones;
    private final OrdenLabRepo ordenes;
    private final InterconsultaRepo interconsultas;
    private final CirugiaRepo cirugias;
    private final AlertaRepo alertas;
    private final PermisoService permisos;
    private final ConfigService config;
    private final ConsecutivoService consecutivos;
    private final FacturacionService facturacion;
    private final PasswordEncoder encoder;

    public DatosDemo(UsuarioRepo usuarios, EpsRepo eps, CatalogoRepo catalogos, MedicamentoRepo medicamentos, LoteRepo lotes,
                     MovimientoRepo movimientos, ExamenLabRepo examenes, CamaRepo camas, PacienteRepo pacientes, AgendaRepo agendas,
                     CitaRepo citas, AdmisionRepo admisiones, SignosRepo signos, TriajeRepo triajes, AtencionRepo atenciones,
                     PrescripcionRepo prescripciones, OrdenLabRepo ordenes, InterconsultaRepo interconsultas, CirugiaRepo cirugias,
                     AlertaRepo alertas, PermisoService permisos, ConfigService config, ConsecutivoService consecutivos,
                     FacturacionService facturacion, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.eps = eps;
        this.catalogos = catalogos;
        this.medicamentos = medicamentos;
        this.lotes = lotes;
        this.movimientos = movimientos;
        this.examenes = examenes;
        this.camas = camas;
        this.pacientes = pacientes;
        this.agendas = agendas;
        this.citas = citas;
        this.admisiones = admisiones;
        this.signos = signos;
        this.triajes = triajes;
        this.atenciones = atenciones;
        this.prescripciones = prescripciones;
        this.ordenes = ordenes;
        this.interconsultas = interconsultas;
        this.cirugias = cirugias;
        this.alertas = alertas;
        this.permisos = permisos;
        this.config = config;
        this.consecutivos = consecutivos;
        this.facturacion = facturacion;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        permisos.inicializar();
        config.get();
        if (usuarios.count() > 0) {
            return;
        }
        if (!activo) {
            usuario("admin", "Administrador del Sistema", "1000000001", "ADMIN", null, null);
            return;
        }
        System.out.println(">> Cargando datos de demostración del HIS Clínica Santa Helena...");
        cargarEps();
        cargarCatalogos();
        cargarMedicamentos();
        cargarExamenes();
        cargarCamas();
        cargarPersonas();
        System.out.println(">> Datos de demostración listos. Usuarios con clave " + CLAVE_DEMO);
    }

    // ------------------------------------------------------------------ usuarios

    private Usuario usuario(String username, String nombre, String doc, String rol, String especialidad, String registro) {
        Usuario u = new Usuario();
        u.username = username;
        u.nombre = nombre;
        u.documento = doc;
        u.email = username + "@santahelena.demo";
        u.roles.add(rol);
        u.passwordHash = encoder.encode(CLAVE_DEMO);
        u.especialidad = especialidad;
        u.registroMedico = registro;
        return usuarios.save(u);
    }

    // ------------------------------------------------------------------ maestros

    private void cargarEps() {
        String[][] d = {
            {"EPS037", "Nueva EPS", "CONTRIBUTIVO", "900.156.264-2", "1.00"},
            {"EPS005", "EPS Sanitas", "CONTRIBUTIVO", "800.251.440-6", "1.05"},
            {"EPS010", "EPS Sura", "CONTRIBUTIVO", "800.088.702-2", "1.08"},
            {"EPS002", "Salud Total EPS", "CONTRIBUTIVO", "800.130.907-4", "1.03"},
            {"ESS024", "Coosalud EPS", "SUBSIDIADO", "900.226.715-3", "0.92"},
            {"EPSS41", "Nueva EPS Subsidiado", "SUBSIDIADO", "900.156.264-2", "0.95"},
            {"POL001", "Póliza Seguros Bolívar (prepagada)", "PREPAGADA", "860.002.503-2", "1.25"}
        };
        for (String[] x : d) {
            Eps e = new Eps();
            e.codigo = x[0];
            e.nombre = x[1];
            e.tipo = x[2];
            e.nit = x[3];
            e.factorTarifa = Double.parseDouble(x[4]);
            eps.save(e);
        }
    }

    private void cargarCatalogos() {
        String[][] cie = {
            {"A09X", "Diarrea y gastroenteritis de presunto origen infeccioso"}, {"B34.9", "Infección viral, no especificada"},
            {"E11.9", "Diabetes mellitus tipo 2 sin complicaciones"}, {"E87.6", "Hipopotasemia"}, {"E87.5", "Hiperpotasemia"},
            {"I10X", "Hipertensión esencial (primaria)"}, {"I20.0", "Angina inestable"}, {"I21.9", "Infarto agudo de miocardio, sin otra especificación"},
            {"I50.0", "Insuficiencia cardíaca congestiva"}, {"I63.9", "Infarto cerebral, no especificado"},
            {"J02.9", "Faringitis aguda, no especificada"}, {"J06.9", "Infección aguda de las vías respiratorias superiores"},
            {"J18.9", "Neumonía, no especificada"}, {"J44.1", "EPOC con exacerbación aguda"}, {"J45.9", "Asma, no especificada"},
            {"K29.7", "Gastritis, no especificada"}, {"K35.8", "Apendicitis aguda, otras y las no especificadas"},
            {"K80.2", "Cálculo de la vesícula biliar sin colecistitis"}, {"K92.2", "Hemorragia gastrointestinal, no especificada"},
            {"M54.5", "Lumbago no especificado"}, {"N17.9", "Insuficiencia renal aguda, no especificada"},
            {"N39.0", "Infección de vías urinarias, sitio no especificado"}, {"R07.4", "Dolor en el pecho, no especificado"},
            {"R10.4", "Otros dolores abdominales y los no especificados"}, {"R50.9", "Fiebre, no especificada"},
            {"R51X", "Cefalea"}, {"R55X", "Síncope y colapso"}, {"S06.0", "Concusión"}, {"S52.5", "Fractura de la epífisis inferior del radio"},
            {"T78.4", "Alergia no especificada"}, {"Z00.0", "Examen médico general"}, {"Z30.0", "Consejo y asesoramiento general sobre la anticoncepción"},
            {"O80.0", "Parto único espontáneo, presentación cefálica de vértice"}, {"A90X", "Fiebre del dengue (dengue clásico)"},
            {"F41.1", "Trastorno de ansiedad generalizada"}, {"L03.1", "Celulitis de otras partes de los miembros"}
        };
        for (String[] c : cie) {
            catalogos.save(CatalogoItem.de("CIE10", c[0], c[1], null));
        }
        Object[][] cups = {
            {"890201", "Consulta de primera vez por medicina general", 45000.0},
            {"890301", "Consulta de control o de seguimiento por medicina general", 38000.0},
            {"890202", "Consulta de primera vez por medicina especializada", 85000.0},
            {"890701", "Consulta de urgencias por medicina general", 65000.0},
            {"890602", "Atención (visita) intrahospitalaria por medicina general", 40000.0},
            {"DC-001", "Día cama hospitalización general (código interno)", 280000.0},
            {"902210", "Hemograma IV (hemoglobina, hematocrito, recuento de eritrocitos…)", 28000.0},
            {"903841", "Glucosa en suero u otro fluido diferente a orina", 9000.0},
            {"903895", "Creatinina en suero u otros fluidos", 12000.0},
            {"903859", "Potasio en suero u otros fluidos", 14000.0},
            {"903864", "Sodio en suero u otros fluidos", 14000.0},
            {"907106", "Uroanálisis", 15000.0},
            {"470101", "Apendicectomía", 1850000.0},
            {"512301", "Colecistectomía por laparoscopia", 3200000.0}
        };
        for (Object[] c : cups) {
            catalogos.save(CatalogoItem.de("CUPS", (String) c[0], (String) c[1], (Double) c[2]));
        }
    }

    private void cargarMedicamentos() {
        // nombre, cum, forma, vía, grupo, control, mínimo, precio, lotes {numero, meses para vencer, cantidad}
        Object[][] d = {
            {"Acetaminofén 500 mg tableta", "19930117-01", "Tableta", "Oral", "ANALGESICO", false, 200, 120.0, new Object[][] {{"AC2311", 3, 80}, {"AC2405", 14, 900}}},
            {"Ibuprofeno 400 mg tableta", "19951542-02", "Tableta", "Oral", "AINE", false, 150, 180.0, new Object[][] {{"IB2402", 10, 600}}},
            {"Naproxeno 250 mg tableta", "19944311-01", "Tableta", "Oral", "AINE", false, 100, 210.0, new Object[][] {{"NP2310", 7, 300}}},
            {"Diclofenaco 75 mg/3 mL ampolla", "19960881-03", "Solución inyectable", "Intramuscular", "AINE", false, 50, 1900.0, new Object[][] {{"DC2401", 12, 140}}},
            {"Amoxicilina 500 mg cápsula", "19934455-01", "Cápsula", "Oral", "PENICILINA", false, 200, 290.0, new Object[][] {{"AM2312", 8, 700}}},
            {"Ampicilina + sulbactam 1,5 g vial", "20001234-01", "Polvo para inyección", "Intravenosa", "PENICILINA", false, 30, 9800.0, new Object[][] {{"AS2402", 18, 60}}},
            {"Cefalexina 500 mg cápsula", "19938876-02", "Cápsula", "Oral", "CEFALOSPORINA", false, 100, 420.0, new Object[][] {{"CF2401", 15, 260}}},
            {"Ceftriaxona 1 g vial", "19956677-04", "Polvo para inyección", "Intravenosa", "CEFALOSPORINA", false, 40, 3500.0, new Object[][] {{"CT2309", -1, 25}, {"CT2403", 9, 22}}},
            {"Azitromicina 500 mg tableta", "19972233-01", "Tableta", "Oral", "MACROLIDO", false, 60, 1500.0, new Object[][] {{"AZ2404", 20, 180}}},
            {"Enalapril 20 mg tableta", "19921188-01", "Tableta", "Oral", "IECA", false, 150, 95.0, new Object[][] {{"EN2402", 16, 800}}},
            {"Losartán 50 mg tableta", "19985544-02", "Tableta", "Oral", "ARA-II", false, 150, 110.0, new Object[][] {{"LS2401", 13, 650}}},
            {"Metformina 850 mg tableta", "19917733-01", "Tableta", "Oral", "ANTIDIABETICO", false, 200, 85.0, new Object[][] {{"MF2403", 22, 1200}}},
            {"Insulina glargina 100 UI/mL pluma", "20045566-01", "Solución inyectable", "Subcutánea", "INSULINA", false, 10, 62000.0, new Object[][] {{"IG2405", 6, 18}}},
            {"Omeprazol 20 mg cápsula", "19962211-03", "Cápsula", "Oral", "IBP", false, 200, 130.0, new Object[][] {{"OM2404", 19, 950}}},
            {"Metoclopramida 10 mg/2 mL ampolla", "19948899-01", "Solución inyectable", "Intravenosa", "ANTIEMETICO", false, 40, 850.0, new Object[][] {{"MT2312", 2, 35}}},
            {"Salbutamol 100 mcg inhalador", "19976655-02", "Aerosol", "Inhalada", "BRONCODILATADOR", false, 20, 8900.0, new Object[][] {{"SB2402", 11, 45}}},
            {"Atorvastatina 20 mg tableta", "20012345-01", "Tableta", "Oral", "ESTATINA", false, 100, 240.0, new Object[][] {{"AT2401", 17, 400}}},
            {"Cloruro de sodio 0,9 % bolsa 500 mL", "19990011-05", "Solución inyectable", "Intravenosa", "LIQUIDOS", false, 100, 3100.0, new Object[][] {{"SS2404", 24, 320}}},
            {"Morfina 10 mg/mL ampolla", "19905566-01", "Solución inyectable", "Intravenosa", "OPIOIDE", true, 20, 4200.0, new Object[][] {{"MO2402", 10, 40}}},
            {"Tramadol 50 mg cápsula", "19933221-02", "Cápsula", "Oral", "OPIOIDE", true, 50, 380.0, new Object[][] {{"TR2401", 12, 120}}},
            {"Midazolam 5 mg/mL ampolla", "19987766-01", "Solución inyectable", "Intravenosa", "BENZODIAZEPINA", true, 15, 5600.0, new Object[][] {{"MZ2403", 9, 12}}},
            {"Clonazepam 2 mg tableta", "19924488-01", "Tableta", "Oral", "BENZODIAZEPINA", true, 60, 260.0, new Object[][] {{"CZ2402", 14, 150}}}
        };
        LocalDate hoy = LocalDate.now();
        for (Object[] x : d) {
            Medicamento m = new Medicamento();
            m.nombre = (String) x[0];
            m.codigoCum = (String) x[1];
            m.formaFarmaceutica = (String) x[2];
            m.viaPredeterminada = (String) x[3];
            m.grupo = (String) x[4];
            m.controlEspecial = (Boolean) x[5];
            m.stockMinimo = (Integer) x[6];
            m.precio = (Double) x[7];
            m = medicamentos.save(m);
            int saldo = 0;
            for (Object[] l : (Object[][]) x[8]) {
                Lote lo = new Lote();
                lo.medicamentoId = m.id;
                lo.medicamentoNombre = m.nombre;
                lo.numero = (String) l[0];
                lo.vence = hoy.plusMonths((Integer) l[1]);
                lo.cantidad = (Integer) l[2];
                lo.proveedor = "Distribuidora Farmacéutica del Oriente";
                lo.fechaIngreso = LocalDateTime.now().minusDays(40);
                lotes.save(lo);
                saldo += lo.cantidad;
                Movimiento mv = new Movimiento();
                mv.fecha = lo.fechaIngreso;
                mv.medicamentoId = m.id;
                mv.medicamentoNombre = m.nombre;
                mv.controlEspecial = m.controlEspecial;
                mv.loteNumero = lo.numero;
                mv.tipo = "ENTRADA";
                mv.cantidad = lo.cantidad;
                mv.saldo = saldo;
                mv.usuario = "Carga inicial";
                mv.referencia = "Inventario inicial";
                movimientos.save(mv);
            }
        }
    }

    private ExamenLab.Analito an(String n, String u, Double min, Double max, Double cmin, Double cmax) {
        ExamenLab.Analito a = new ExamenLab.Analito();
        a.nombre = n;
        a.unidad = u;
        a.min = min;
        a.max = max;
        a.criticoMin = cmin;
        a.criticoMax = cmax;
        return a;
    }

    private void examen(String codigo, String nombre, String muestra, double precio, ExamenLab.Analito... as) {
        ExamenLab e = new ExamenLab();
        e.codigo = codigo;
        e.nombre = nombre;
        e.muestra = muestra;
        e.precio = precio;
        e.analitos = new ArrayList<>(List.of(as));
        examenes.save(e);
    }

    private void cargarExamenes() {
        examen("902210", "Hemograma IV", "Sangre total EDTA", 28000,
            an("Hemoglobina", "g/dL", 12.0, 16.0, 7.0, 20.0),
            an("Hematocrito", "%", 36.0, 48.0, 21.0, 60.0),
            an("Leucocitos", "x10³/µL", 4.5, 11.0, 2.0, 30.0),
            an("Plaquetas", "x10³/µL", 150.0, 450.0, 50.0, 1000.0));
        examen("903841", "Glucosa en suero", "Suero", 9000, an("Glucosa", "mg/dL", 70.0, 100.0, 40.0, 400.0));
        examen("903895", "Creatinina en suero", "Suero", 12000, an("Creatinina", "mg/dL", 0.6, 1.2, null, 5.0));
        examen("903859", "Potasio en suero", "Suero", 14000, an("Potasio", "mmol/L", 3.5, 5.1, 2.8, 6.2));
        examen("903864", "Sodio en suero", "Suero", 14000, an("Sodio", "mmol/L", 135.0, 145.0, 120.0, 160.0));
        examen("907106", "Uroanálisis", "Orina", 15000,
            an("pH", "", 5.0, 8.0, null, null),
            an("Densidad", "", 1.005, 1.030, null, null),
            an("Leucocitos (sedimento)", "x campo", 0.0, 5.0, null, null));
    }

    private void cama(String codigo, String servicio, String piso, String hab, String estado, String sexo, boolean aisl) {
        Cama c = new Cama();
        c.codigo = codigo;
        c.servicio = servicio;
        c.piso = piso;
        c.habitacion = hab;
        c.estado = estado;
        c.sexoPermitido = sexo;
        c.aislamiento = aisl;
        c.desde = LocalDateTime.now().minusHours(5);
        if ("BLOQUEADA".equals(estado)) {
            c.motivoBloqueo = "Mantenimiento de la toma de oxígeno";
        }
        camas.save(c);
    }

    private void cargarCamas() {
        cama("MI-201", "Medicina Interna", "2", "201", "LIBRE", "M", false);
        cama("MI-202", "Medicina Interna", "2", "202", "LIBRE", "F", false);
        cama("MI-203", "Medicina Interna", "2", "203", "LIMPIEZA", null, false);
        cama("MI-204", "Medicina Interna", "2", "204", "LIBRE", null, true);
        cama("CX-301", "Cirugía", "3", "301", "LIBRE", "M", false);
        cama("CX-302", "Cirugía", "3", "302", "LIBRE", "F", false);
        cama("CX-303", "Cirugía", "3", "303", "BLOQUEADA", null, false);
        cama("PED-401", "Pediatría", "4", "401", "LIBRE", null, false);
        cama("PED-402", "Pediatría", "4", "402", "LIBRE", null, true);
        cama("UCI-01", "UCI Adultos", "5", "UCI", "LIBRE", null, false);
        cama("UCI-02", "UCI Adultos", "5", "UCI", "LIBRE", null, true);
        cama("UCI-03", "UCI Adultos", "5", "UCI", "LIMPIEZA", null, false);
        cama("UCI-04", "UCI Adultos", "5", "UCI", "LIBRE", null, false);
    }

    // ------------------------------------------------------------------ pacientes y actividad clínica

    private Paciente paciente(String tipo, String num, String nombres, String apellidos, LocalDate nac, String sexo, String epsCodigo,
                              String regimen, String tel, String municipio, String... alergias) {
        Paciente p = new Paciente();
        p.numeroHistoria = consecutivos.formato("historia", "HC-", 6);
        p.tipoDocumento = tipo;
        p.numeroDocumento = num;
        p.nombres = nombres;
        p.apellidos = apellidos;
        p.fechaNacimiento = nac;
        p.sexo = sexo;
        Eps e = eps.findByCodigo(epsCodigo).orElse(null);
        if (e != null) {
            p.epsId = e.id;
            p.epsNombre = e.nombre;
        }
        p.regimen = regimen;
        p.nivel = "SUBSIDIADO".equals(regimen) ? "Nivel 1" : "Categoría A";
        p.telefono = tel;
        p.municipio = municipio;
        p.direccion = "Calle " + (10 + num.length()) + " # " + num.substring(0, 2) + "-" + num.substring(num.length() - 2);
        p.alergias = new ArrayList<>(List.of(alergias));
        p.consentimientoDatos = LocalDateTime.now().minusMonths(6);
        char u = num.charAt(num.length() - 1);
        p.derechosEstado = u == '9' ? "RETIRADO" : (u == '8' ? "SUSPENDIDO" : "ACTIVO");
        p.derechosFecha = LocalDateTime.now().minusDays(1);
        p.creadoPor = "Carga inicial";
        p.actualizarBusqueda();
        return pacientes.save(p);
    }

    private SignosVitales signos(Paciente p, String admisionId, LocalDateTime f, int sis, int dia, int fc, int fr, double t, int sat, Integer gl, String quien) {
        SignosVitales s = new SignosVitales();
        s.pacienteId = p.id;
        s.admisionId = admisionId;
        s.fecha = f;
        s.taSistolica = sis;
        s.taDiastolica = dia;
        s.fc = fc;
        s.fr = fr;
        s.temperatura = t;
        s.saturacion = sat;
        s.glucometria = gl;
        s.usuario = quien;
        s.alertas = co.santahelena.his.util.Reglas.alertasSignos(s, config.get());
        return signos.save(s);
    }

    private Admision urgencias(Paciente p, String motivo, String estado, Integer nivel, int minutosAtras) {
        Admision a = new Admision();
        a.pacienteId = p.id;
        a.pacienteNombre = p.nombreCompleto();
        a.pacienteDocumento = p.documentoTexto();
        a.pacienteSexo = p.sexo;
        a.tipo = "URGENCIAS";
        a.estado = estado;
        a.motivo = motivo;
        a.epsNombre = p.epsNombre;
        a.derechosEstado = p.derechosEstado;
        a.fechaIngreso = LocalDateTime.now().minusMinutes(minutosAtras);
        a.creadoPor = "Diana Marcela Pinzón Reyes";
        a.triajeNivel = nivel;
        if (nivel != null) {
            a.triajeFecha = a.fechaIngreso.plusMinutes(8);
        }
        return admisiones.save(a);
    }

    private Atencion.Diagnostico dx(String codigo, String desc, String tipo) {
        Atencion.Diagnostico d = new Atencion.Diagnostico();
        d.codigo = codigo;
        d.descripcion = desc;
        d.tipo = tipo;
        return d;
    }

    private Prescripcion.Item item(Medicamento m, String dosis, String frecuencia, int dias, int cantidad) {
        Prescripcion.Item i = new Prescripcion.Item();
        i.medicamentoId = m.id;
        i.codigoCum = m.codigoCum;
        i.nombre = m.nombre;
        i.dosis = dosis;
        i.via = m.viaPredeterminada;
        i.frecuencia = frecuencia;
        i.dias = dias;
        i.cantidad = cantidad;
        i.controlEspecial = m.controlEspecial;
        return i;
    }

    private Medicamento med(String cum) {
        return medicamentos.findByCodigoCum(cum).orElseThrow();
    }

    private void cargarPersonas() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();

        // Pacientes
        Paciente andrea = paciente("CC", "1098765432", "Andrea Carolina", "Rojas Pérez", LocalDate.of(1992, 5, 14), "F", "EPS037", "CONTRIBUTIVO", "3157894561", "Bucaramanga", "Penicilina");
        andrea.email = "paciente@santahelena.demo";
        pacientes.save(andrea);
        Paciente luis = paciente("CC", "91234567", "Luis Alberto", "Gómez Mantilla", LocalDate.of(1958, 3, 2), "M", "EPS005", "CONTRIBUTIVO", "3004561278", "Floridablanca");
        Paciente maria = paciente("CC", "63456781", "María Fernanda", "Ortiz Silva", LocalDate.of(1975, 9, 21), "F", "EPS010", "CONTRIBUTIVO", "3112345678", "Girón");
        Paciente jorge = paciente("CC", "13789012", "Jorge Enrique", "Castillo Ruiz", LocalDate.of(1966, 11, 7), "M", "ESS024", "SUBSIDIADO", "3209876543", "Piedecuesta", "AINE");
        Paciente sofia = paciente("TI", "1095123456", "Sofía Valentina", "Martínez Díaz", LocalDate.of(2012, 2, 18), "F", "EPS002", "CONTRIBUTIVO", "3176543210", "Bucaramanga");
        Paciente carlos = paciente("CC", "1102345670", "Carlos Andrés", "Peña Vargas", LocalDate.of(1988, 7, 30), "M", "EPS037", "CONTRIBUTIVO", "3014567890", "Bucaramanga");
        Paciente rosa = paciente("CC", "37845619", "Rosa Elena", "Quintero de Arias", LocalDate.of(1949, 1, 25), "F", "EPSS41", "SUBSIDIADO", "6076451234", "Lebrija", "Sulfas");
        Paciente mateo = paciente("RC", "1096000123", "Mateo", "Ruiz Cárdenas", hoy.minusMonths(20), "M", "EPS005", "CONTRIBUTIVO", "3188765432", "Floridablanca");
        mateo.acompananteNombre = "Laura Cárdenas Mejía";
        mateo.acompananteParentesco = "Madre";
        mateo.acompananteTelefono = "3188765432";
        pacientes.save(mateo);

        // Usuarios (uno por rol)
        usuario("admin", "Administrador del Sistema", "1000000001", "ADMIN", null, null);
        usuario("recepcion", "Diana Marcela Pinzón Reyes", "1000000002", "RECEPCION", null, null);
        Usuario pac = usuario("paciente", andrea.nombreCompleto(), andrea.numeroDocumento, "PACIENTE", null, null);
        pac.pacienteId = andrea.id;
        pac.email = andrea.email;
        usuarios.save(pac);
        Usuario enf = usuario("enfermeria", "Paola Andrea Niño Rueda", "1000000004", "ENFERMERIA", null, "TP-ENF 68-4521");
        Usuario med = usuario("medico", "Dr. Andrés Felipe Suárez Gómez", "1000000005", "MEDICO", "Medicina General", "RM 68-12345");
        Usuario esp = usuario("especialista", "Dra. Natalia Prada Arenas", "1000000006", "ESPECIALISTA", "Medicina Interna", "RM 68-23456");
        Usuario lab = usuario("laboratorio", "Bact. Camilo Ernesto Duarte León", "1000000007", "LABORATORIO", null, null);
        Usuario far = usuario("farmacia", "Q.F. Lorena Patricia Villamizar", "1000000008", "FARMACIA", null, null);
        usuario("facturacion", "Sandra Milena Cárdenas Ortiz", "1000000009", "FACTURACION", null, null);
        usuario("camas", "Óscar Iván Rincón Bautista", "1000000010", "CAMAS", null, null);

        // Agendas
        Agenda ag1 = new Agenda();
        ag1.profesionalId = med.id;
        ag1.profesionalNombre = med.nombre;
        ag1.especialidad = "Medicina General";
        ag1.sede = "Sede Cabecera";
        ag1.consultorio = "Consultorio 104";
        ag1.dias = Set.of(1, 2, 3, 4, 5);
        agendas.save(ag1);
        Agenda ag2 = new Agenda();
        ag2.profesionalId = esp.id;
        ag2.profesionalNombre = esp.nombre;
        ag2.especialidad = "Medicina Interna";
        ag2.sede = "Sede Cabecera";
        ag2.consultorio = "Consultorio 210";
        ag2.dias = Set.of(2, 4);
        ag2.duracionMinutos = 30;
        ag2.tardeInicio = "";
        ag2.tardeFin = "";
        agendas.save(ag2);

        // Citas (hoy para el médico, futura y pasada para Andrea)
        cita(rosa, ag1, hoy.atTime(LocalTime.of(8, 0)), "ASIGNADA", "Control hipertensión", "VENTANILLA");
        cita(mateo, ag1, hoy.atTime(LocalTime.of(8, 20)), "ASIGNADA", "Primera vez", "VENTANILLA");
        cita(carlos, ag1, hoy.atTime(LocalTime.of(9, 0)), "ASIGNADA", "Primera vez", "PORTAL");
        cita(andrea, ag1, siguienteHabil(hoy.plusDays(3)).atTime(LocalTime.of(9, 40)), "ASIGNADA", "Control", "PORTAL");
        cita(andrea, ag2, siguienteHabil(hoy.plusDays(9)).atTime(LocalTime.of(10, 0)), "ASIGNADA", "Primera vez", "VENTANILLA");
        Cita pasada = cita(andrea, ag1, hoy.minusDays(12).atTime(LocalTime.of(10, 20)), "ATENDIDA", "Primera vez", "PORTAL");

        // Historia previa de Andrea con fórmula activa y laboratorio validado (para el portal)
        Atencion a1 = new Atencion();
        a1.pacienteId = andrea.id;
        a1.pacienteNombre = andrea.nombreCompleto();
        a1.citaId = pasada.id;
        a1.profesionalId = med.id;
        a1.profesionalNombre = med.nombre;
        a1.profesionalRegistro = med.registroMedico;
        a1.especialidad = "Medicina General";
        a1.fecha = pasada.fechaHora;
        a1.motivoConsulta = "Dolor de garganta y fiebre de 3 días";
        a1.enfermedadActual = "Odinofagia intensa, fiebre no cuantificada, malestar general. Niega tos.";
        a1.antecedentes = "Alergia a penicilina (urticaria). Sin enfermedades crónicas.";
        a1.examenFisico = "T 38,1 °C. Faringe eritematosa sin exudado. Sin adenopatías.";
        a1.analisis = "Faringitis aguda de probable origen viral.";
        a1.plan = "Manejo sintomático, hidratación, hemograma de control. Signos de alarma explicados.";
        a1.diagnosticos.add(dx("J02.9", "Faringitis aguda, no especificada", "Principal"));
        a1.firmada = true;
        a1.fechaFirma = a1.fecha.plusMinutes(18);
        a1.hashFirma = Hash.sha256("demo|" + a1.pacienteId + a1.fechaFirma);
        a1 = atenciones.save(a1);
        facturacion.cargar(andrea.id, "890201", "Consulta de primera vez por medicina general", 1, 45000, "CONSULTA", "ATN-" + a1.id);

        Prescripcion pr1 = new Prescripcion();
        pr1.pacienteId = andrea.id;
        pr1.pacienteNombre = andrea.nombreCompleto();
        pr1.pacienteDocumento = andrea.documentoTexto();
        pr1.atencionId = a1.id;
        pr1.medicoId = med.id;
        pr1.medicoNombre = med.nombre;
        pr1.medicoRegistro = med.registroMedico;
        pr1.fecha = a1.fechaFirma;
        pr1.venceEn = hoy.plusDays(18);
        pr1.items.add(item(med("19930117-01"), "1 tableta", "Cada 8 horas", 5, 15));
        pr1.items.add(item(med("19962211-03"), "1 cápsula", "Cada 24 horas en ayunas", 14, 14));
        pr1.validacionFarmacia = "VALIDADA";
        pr1.validadaPor = far.nombre;
        pr1.validadaEn = a1.fechaFirma.plusHours(1);
        pr1.hashFirma = Hash.sha256("demo-formula-" + andrea.id);
        prescripciones.save(pr1);

        OrdenLab o1 = new OrdenLab();
        o1.pacienteId = andrea.id;
        o1.pacienteNombre = andrea.nombreCompleto();
        o1.pacienteDocumento = andrea.documentoTexto();
        o1.atencionId = a1.id;
        o1.medicoId = med.id;
        o1.medicoNombre = med.nombre;
        o1.fecha = a1.fechaFirma;
        o1.estado = "VALIDADA";
        o1.codigoMuestra = consecutivos.formato("muestra", "LAB", 7);
        o1.recibidaEn = o1.fecha.plusHours(20);
        o1.recibidaPor = lab.nombre;
        o1.resultadosEn = o1.recibidaEn.plusHours(2);
        o1.resultadosPor = lab.nombre;
        o1.validadaEn = o1.resultadosEn.plusMinutes(30);
        o1.validadaPor = lab.nombre;
        OrdenLab.ExamenOrden eo = new OrdenLab.ExamenOrden();
        eo.codigo = "902210";
        eo.nombre = "Hemograma IV";
        eo.resultados.add(res("Hemoglobina", 13.4, "g/dL", "12.0 – 16.0", false));
        eo.resultados.add(res("Hematocrito", 40.2, "%", "36.0 – 48.0", false));
        eo.resultados.add(res("Leucocitos", 11.8, "x10³/µL", "4.5 – 11.0", true));
        eo.resultados.add(res("Plaquetas", 265.0, "x10³/µL", "150.0 – 450.0", false));
        o1.examenes.add(eo);
        ordenes.save(o1);
        facturacion.cargar(andrea.id, "902210", "Hemograma IV", 1, 28000, "LABORATORIO", "LAB-" + o1.id + "-902210");

        // Urgencias: un paciente sin triaje y dos ya clasificados
        Admision uMaria = urgencias(maria, "Dolor abdominal intenso en fosa ilíaca derecha desde la madrugada", "EN_ESPERA_TRIAJE", null, 12);
        Admision uJorge = urgencias(jorge, "Debilidad generalizada y palpitaciones", "TRIADO", 2, 55);
        Admision uSofia = urgencias(sofia, "Fiebre y dolor de oído derecho", "TRIADO", 4, 80);
        triaje(uJorge, jorge, 2, 2, signos(jorge, uJorge.id, uJorge.triajeFecha, 152, 94, 124, 22, 36.8, 95, 132, enf.nombre), enf.nombre);
        triaje(uSofia, sofia, 4, 4, signos(sofia, uSofia.id, uSofia.triajeFecha, 108, 68, 96, 18, 38.2, 98, null, enf.nombre), enf.nombre);
        alertas.save(alerta("MEDICO", "TRIAJE", "ALTA", jorge, "Paciente clasificado TRIAJE II en urgencias: atención en máximo 30 minutos.", "/hce"));

        OrdenLab o2 = new OrdenLab();
        o2.pacienteId = jorge.id;
        o2.pacienteNombre = jorge.nombreCompleto();
        o2.pacienteDocumento = jorge.documentoTexto();
        o2.medicoId = med.id;
        o2.medicoNombre = med.nombre;
        o2.prioridad = "URGENTE";
        o2.fecha = ahora.minusMinutes(25);
        o2.observaciones = "Descartar alteración hidroelectrolítica";
        o2.examenes.add(exOrden("903859", "Potasio en suero"));
        o2.examenes.add(exOrden("903895", "Creatinina en suero"));
        ordenes.save(o2);

        Interconsulta ic = new Interconsulta();
        ic.pacienteId = jorge.id;
        ic.pacienteNombre = jorge.nombreCompleto();
        ic.solicitanteNombre = med.nombre;
        ic.especialidad = "Medicina Interna";
        ic.motivo = "Taquicardia persistente e hipertensión en paciente con debilidad generalizada; solicito valoración.";
        ic.prioridad = "URGENTE";
        ic.fecha = ahora.minusMinutes(20);
        interconsultas.save(ic);

        // Hospitalizado en MI-201
        Admision h = new Admision();
        h.pacienteId = luis.id;
        h.pacienteNombre = luis.nombreCompleto();
        h.pacienteDocumento = luis.documentoTexto();
        h.pacienteSexo = "M";
        h.tipo = "HOSPITALIZACION";
        h.estado = "EN_CAMA";
        h.servicio = "Medicina Interna";
        h.motivo = "J18.9 Neumonía, no especificada";
        h.ordenadoPor = esp.nombre;
        h.epsNombre = luis.epsNombre;
        h.fechaIngreso = ahora.minusDays(3).minusHours(4);
        h.inicioEstancia = ahora.minusDays(3);
        h.creadoPor = esp.nombre;
        h = admisiones.save(h);
        Cama c201 = camas.findByCodigo("MI-201").orElseThrow();
        c201.estado = "OCUPADA";
        c201.pacienteId = luis.id;
        c201.pacienteNombre = luis.nombreCompleto();
        c201.admisionId = h.id;
        c201.desde = h.inicioEstancia;
        camas.save(c201);
        h.camaId = c201.id;
        h.camaCodigo = c201.codigo;
        admisiones.save(h);
        signos(luis, h.id, ahora.minusHours(14), 132, 84, 92, 22, 38.4, 91, 118, enf.nombre);
        signos(luis, h.id, ahora.minusHours(6), 126, 80, 88, 20, 37.6, 93, 110, enf.nombre);

        Atencion a2 = new Atencion();
        a2.pacienteId = luis.id;
        a2.pacienteNombre = luis.nombreCompleto();
        a2.admisionId = h.id;
        a2.profesionalId = esp.id;
        a2.profesionalNombre = esp.nombre;
        a2.profesionalRegistro = esp.registroMedico;
        a2.especialidad = "Medicina Interna";
        a2.tipo = "ESPECIALIDAD";
        a2.fecha = h.fechaIngreso.plusHours(1);
        a2.motivoConsulta = "Tos productiva, fiebre y disnea de 5 días";
        a2.enfermedadActual = "Paciente hipertenso con cuadro respiratorio progresivo.";
        a2.antecedentes = "HTA en manejo con enalapril. Exfumador.";
        a2.examenFisico = "Crépitos en base derecha. SatO2 89 % al aire ambiente.";
        a2.analisis = "Neumonía adquirida en la comunidad CURB-65 de 2. Requiere hospitalización.";
        a2.plan = "Hospitalizar en Medicina Interna, ceftriaxona IV, oxígeno por cánula, control de signos cada 6 horas.";
        a2.diagnosticos.add(dx("J18.9", "Neumonía, no especificada", "Principal"));
        a2.diagnosticos.add(dx("I10X", "Hipertensión esencial (primaria)", "Relacionado"));
        a2.ordenHospitalizacion = true;
        a2.servicioHospitalizacion = "Medicina Interna";
        a2.firmada = true;
        a2.fechaFirma = a2.fecha.plusMinutes(25);
        a2.hashFirma = Hash.sha256("demo|" + luis.id + a2.fechaFirma);
        a2 = atenciones.save(a2);
        facturacion.cargar(luis.id, "890202", "Consulta de primera vez por medicina especializada", 1, 85000, "CONSULTA", "ATN-" + a2.id);

        Prescripcion pr2 = new Prescripcion();
        pr2.pacienteId = luis.id;
        pr2.pacienteNombre = luis.nombreCompleto();
        pr2.pacienteDocumento = luis.documentoTexto();
        pr2.atencionId = a2.id;
        pr2.medicoId = esp.id;
        pr2.medicoNombre = esp.nombre;
        pr2.medicoRegistro = esp.registroMedico;
        pr2.fecha = a2.fechaFirma;
        pr2.venceEn = hoy.plusDays(27);
        pr2.items.add(item(med("19956677-04"), "1 g", "Cada 24 horas", 7, 7));
        pr2.items.add(item(med("19921188-01"), "20 mg", "Cada 12 horas", 7, 14));
        pr2.items.add(item(med("19930117-01"), "1 g (2 tabletas)", "Cada 8 horas si fiebre", 5, 30));
        pr2.validacionFarmacia = "PENDIENTE";
        pr2.hashFirma = Hash.sha256("demo-formula-" + luis.id);
        prescripciones.save(pr2);

        OrdenLab o3 = new OrdenLab();
        o3.pacienteId = luis.id;
        o3.pacienteNombre = luis.nombreCompleto();
        o3.pacienteDocumento = luis.documentoTexto();
        o3.medicoId = esp.id;
        o3.medicoNombre = esp.nombre;
        o3.fecha = ahora.minusHours(3);
        o3.estado = "RECIBIDA";
        o3.codigoMuestra = consecutivos.formato("muestra", "LAB", 7);
        o3.recibidaEn = ahora.minusHours(2);
        o3.recibidaPor = lab.nombre;
        o3.examenes.add(exOrden("902210", "Hemograma IV"));
        ordenes.save(o3);

        // Paciente pendiente de cama
        Admision pc = new Admision();
        pc.pacienteId = carlos.id;
        pc.pacienteNombre = carlos.nombreCompleto();
        pc.pacienteDocumento = carlos.documentoTexto();
        pc.pacienteSexo = "M";
        pc.tipo = "HOSPITALIZACION";
        pc.estado = "PENDIENTE_CAMA";
        pc.servicio = "Cirugía";
        pc.motivo = "K35.8 Apendicitis aguda";
        pc.ordenadoPor = med.nombre;
        pc.epsNombre = carlos.epsNombre;
        pc.fechaIngreso = ahora.minusMinutes(40);
        pc.creadoPor = med.nombre;
        admisiones.save(pc);
        alertas.save(alerta("CAMAS", "SOLICITUD_CAMA", "MEDIA", carlos, "Orden de hospitalización en Cirugía.", "/camas/pendientes"));

        Cirugia cx = new Cirugia();
        cx.pacienteId = carlos.id;
        cx.pacienteNombre = carlos.nombreCompleto();
        cx.pacienteDocumento = carlos.documentoTexto();
        cx.procedimientoCodigo = "470101";
        cx.procedimientoNombre = "Apendicectomía";
        cx.quirofano = "Quirófano 2";
        cx.inicio = siguienteHabil(hoy.plusDays(1)).atTime(7, 0);
        cx.duracionMinutos = 90;
        cx.fin = cx.inicio.plusMinutes(90);
        cx.anestesia = "General";
        cx.especialistaNombre = esp.nombre;
        cx.programadaPor = esp.nombre;
        cirugias.save(cx);

        alertas.save(alerta("FARMACIA", "STOCK_MINIMO", "MEDIA", null, "Ceftriaxona 1 g vial por debajo del stock mínimo (lote CT2309 vencido).", "/farmacia/inventario"));
    }

    private LocalDate siguienteHabil(LocalDate d) {
        while (d.getDayOfWeek().getValue() > 5) {
            d = d.plusDays(1);
        }
        return d;
    }

    private Cita cita(Paciente p, Agenda a, LocalDateTime fh, String estado, String tipo, String canal) {
        Cita c = new Cita();
        c.pacienteId = p.id;
        c.pacienteNombre = p.nombreCompleto();
        c.pacienteDocumento = p.documentoTexto();
        c.profesionalId = a.profesionalId;
        c.profesionalNombre = a.profesionalNombre;
        c.especialidad = a.especialidad;
        c.sede = a.sede;
        c.consultorio = a.consultorio;
        c.fechaHora = fh;
        c.estado = estado;
        c.tipo = tipo;
        c.canal = canal;
        c.creadaPor = "Carga inicial";
        return citas.save(c);
    }

    private void triaje(Admision a, Paciente p, int nivel, int sugerido, SignosVitales s, String quien) {
        Triaje t = new Triaje();
        t.admisionId = a.id;
        t.pacienteId = p.id;
        t.pacienteNombre = p.nombreCompleto();
        t.nivel = nivel;
        t.nivelSugerido = sugerido;
        t.motivo = a.motivo;
        t.signosId = s.id;
        t.resumenSignos = "TA " + s.ta() + " FC " + s.fc + " FR " + s.fr + " T " + s.temperatura + " SatO2 " + s.saturacion;
        t.enfermero = quien;
        t.fecha = a.triajeFecha;
        triajes.save(t);
    }

    private OrdenLab.Resultado res(String an, double v, String u, String rango, boolean fuera) {
        OrdenLab.Resultado r = new OrdenLab.Resultado();
        r.analito = an;
        r.valor = v;
        r.unidad = u;
        r.rango = rango;
        r.fueraRango = fuera;
        return r;
    }

    private OrdenLab.ExamenOrden exOrden(String codigo, String nombre) {
        OrdenLab.ExamenOrden e = new OrdenLab.ExamenOrden();
        e.codigo = codigo;
        e.nombre = nombre;
        return e;
    }

    private Alerta alerta(String rol, String tipo, String sev, Paciente p, String msg, String enlace) {
        Alerta a = new Alerta();
        a.rolDestino = rol;
        a.tipo = tipo;
        a.severidad = sev;
        a.pacienteId = p == null ? null : p.id;
        a.pacienteNombre = p == null ? null : p.nombreCompleto();
        a.mensaje = msg;
        a.enlace = enlace;
        return a;
    }
}
