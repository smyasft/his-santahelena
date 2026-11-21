# HIS Clínica Santa Helena

Sistema de Información Hospitalaria (HIS) académico con los **10 roles** del SRS: administrador, recepción/admisiones, paciente, enfermería, médico general, especialista, laboratorio, farmacia, facturación y gestor de camas.

- **Backend:** Java 17 · Spring Boot 3.3 · Spring Security · Spring Data MongoDB
- **Vistas:** Thymeleaf (HTML renderizado en el servidor), con un diseño tipo HIS colombiano
- **Base de datos:** MongoDB 6 o superior (local, Docker o Atlas)

> Proyecto académico. Los datos de demostración son ficticios. La consulta de derechos (ADRES) y el envío de la factura electrónica (DIAN) son **simulaciones**. La única integración real es el **catálogo oficial de medicamentos del INVIMA** (datos.gov.co), descrita en la sección 6.

---

## 1. Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 17 o superior | Compilar y ejecutar |
| Maven | 3.9+ (o el que trae IntelliJ / el wrapper de tu IDE) | Descargar dependencias y compilar |
| MongoDB | 6 o 7 | Base de datos |
| Navegador | Chrome, Edge o Firefox | Usar el sistema |

---

## 2. Configurar MongoDB (elige UNA opción)

### Opción A — MongoDB instalado en tu computador (la más sencilla)

1. Descarga **MongoDB Community Server** desde <https://www.mongodb.com/try/download/community> e instálalo con las opciones por defecto (en Windows deja marcado *Install MongoDB as a Service*).
2. Instala también **MongoDB Compass** (lo ofrece el mismo instalador) para ver los datos.
3. Abre Compass y conéctate a `mongodb://localhost:27017`. Si conecta, ya está.

No tienes que crear la base ni las colecciones: la aplicación crea la base `his_santahelena`, sus colecciones e índices al arrancar.

### Opción B — Docker

Con Docker Desktop instalado, desde la carpeta del proyecto:

```bash
docker compose up -d
```

Esto levanta MongoDB 7 en el puerto 27017 con un volumen persistente (`his-mongo-data`). Para detenerlo: `docker compose down`.

### Opción C — MongoDB Atlas (en la nube, gratis)

1. Crea una cuenta en <https://www.mongodb.com/atlas> y un clúster **M0 (Free)**.
2. En **Database Access** crea un usuario y contraseña.
3. En **Network Access** agrega tu IP (o `0.0.0.0/0` solo para pruebas).
4. En **Connect → Drivers** copia la cadena de conexión y agrégale el nombre de la base:

   ```
   mongodb+srv://USUARIO:CLAVE@cluster0.xxxxx.mongodb.net/his_santahelena?retryWrites=true&w=majority
   ```

5. Pásala a la aplicación con la variable de entorno `MONGODB_URI` (no la escribas en el código):

   - Windows (PowerShell): `$env:MONGODB_URI="mongodb+srv://..."`
   - macOS / Linux: `export MONGODB_URI="mongodb+srv://..."`
   - IntelliJ: *Run → Edit Configurations → Environment variables* → `MONGODB_URI=mongodb+srv://...`

Si no defines `MONGODB_URI`, la aplicación usa `mongodb://localhost:27017/his_santahelena` (archivo `src/main/resources/application.properties`).

---

## 3. Ejecutar

### Con IntelliJ IDEA (recomendado)

1. *File → Open* y selecciona la carpeta `his-santahelena` (la que tiene `pom.xml`). Acepta *Load Maven project*.
2. Espera a que descargue las dependencias (la primera vez tarda unos minutos).
3. Abre `src/main/java/co/santahelena/his/HisApplication.java` y pulsa ▶ *Run*.
4. Entra a <http://localhost:8080>.

### Desde la terminal

```bash
mvn spring-boot:run
```

Para generar el ejecutable: `mvn clean package` y luego `java -jar target/his-santahelena-1.0.0.jar`.

Pruebas unitarias de las reglas de negocio (no necesitan MongoDB): `mvn test`.

### Primer arranque

Si la base está vacía, el sistema carga **datos de demostración**: usuarios, 8 pacientes, EPS, CIE-10, CUPS, 22 medicamentos con lotes, exámenes, 13 camas, citas, pacientes en urgencias, un hospitalizado y fórmulas. En la consola verás `>> Datos de demostración listos`.

- Para empezar con una base limpia (solo el usuario `admin`), pon `his.datos-demo=false` en `application.properties` **antes** del primer arranque.
- Para volver a cargar la demostración, borra la base `his_santahelena` en Compass y reinicia.

---

## 4. Usuarios de demostración

Contraseña de todos: **`Clinica2026*`**

| Usuario | Rol | Qué puede hacer |
|---|---|---|
| `admin` | Administrador | Usuarios, matriz de permisos, auditoría, MFA, rangos de signos, catálogos |
| `recepcion` | Admisiones | Registrar pacientes, verificar derechos, citas, ingreso a urgencias (incluye NN) |
| `paciente` | Paciente (Andrea Rojas) | Agendar o cancelar citas, ver resultados validados y fórmulas |
| `enfermeria` | Enfermería | Cola de triaje, clasificación, signos vitales, administración de medicamentos |
| `medico` | Médico general | Agenda, historia clínica, firma, fórmulas, órdenes de laboratorio, hospitalización y egreso |
| `especialista` | Médico especialista | Interconsultas, consentimientos, programación quirúrgica, historia clínica |
| `laboratorio` | Laboratorio | Recibir muestras (código de barras), resultados, validación, valores críticos |
| `farmacia` | Farmacia | Validar y dispensar fórmulas (FEFO), inventario, lotes, kárdex, control especial |
| `facturacion` | Facturación | Cuentas, factura electrónica (simulada), pagos, convenios, paz y salvo, RIPS |
| `camas` | Gestor de camas | Mapa de camas, asignación, traslados, liquidación de estancia y egreso |

---

## 5. Recorrido sugerido para la sustentación

1. **enfermeria** → *Cola de triaje* → clasifica a *María Fernanda Ortiz* (escribe signos, pulsa *Calcular nivel sugerido*, guarda).
2. **medico** → *Mi agenda* → atiende a *Jorge Castillo* (triaje II, alérgico a AINE): diagnóstico CIE-10 (escribe "hipo"), prescribe *Ibuprofeno* → aparece la alerta de alergia y pide justificación → firma.
3. **laboratorio** → recibe la orden de potasio (se imprime la etiqueta con código de barras) → registra `Potasio = 2.5` → valida → el médico recibe la **alerta de valor crítico**.
4. **farmacia** → valida y dispensa la fórmula de *Luis Gómez* (el lote vencido de ceftriaxona no se usa; se genera alerta de stock mínimo). Luego, en *Catálogo INVIMA*, busca un medicamento real e impórtalo al inventario.
5. **camas** → *Pendientes de cama* → asigna cama a *Carlos Peña* (las camas incompatibles por sexo o servicio aparecen bloqueadas).
6. **medico** → evolución de *Luis Gómez* marcando *orden de egreso* → **camas** liquida la estancia → **facturacion** emite la factura y el paz y salvo → **camas** registra el egreso.
7. **paciente** → agenda una cita y descarga su resultado de laboratorio.
8. **admin** → *Auditoría*: ves todo lo anterior (quién abrió cada historia, accesos denegados, logins fallidos) y lo exportas a CSV.

---

## 6. Integración con el INVIMA (datos.gov.co)

El módulo de farmacia consulta en línea el listado oficial **«Código Único de Medicamentos Vigentes»** que el INVIMA publica en el portal de datos abiertos del Estado: <https://www.datos.gov.co/resource/i7cb-raxc.json> (API SODA de Socrata).

**Cómo se usa:** entra con `farmacia` → **Catálogo INVIMA** (o *Inventario → Agregar desde INVIMA*). Busca, por ejemplo, `losartan`, revisa el resultado y dale *Importar al inventario*. Luego ingresa su primer lote en *Inventario*.

- Solo trae registros **vigentes**, CUM **activos** y que no son muestra médica.
- El **CUM** se arma como `expediente-consecutivo`, igual que en las fórmulas oficiales.
- El sistema **sugiere el grupo farmacológico** (AINE, IECA, ARA-II, OPIOIDE…) a partir del código **ATC**, porque ese grupo es el que usa el motor de alertas de alergias e interacciones. También marca un *posible control especial* para opioides y benzodiazepinas. El farmaceuta confirma ambos antes de importar.
- Al importar, el servidor **vuelve a consultar el CUM** en el INVIMA, de modo que no se guardan datos alterados desde el formulario.
- Cada consulta e importación queda en la **auditoría**.

**Requisitos:** internet en el servidor. No necesita registro. Si se hacen muchas consultas, se puede sacar un *app token* gratuito en datos.gov.co y ponerlo en la variable de entorno `DATOS_GOV_TOKEN`. La URL del dataset se cambia en `his.cum.url` (`application.properties`).

Si datos.gov.co no responde, la página muestra el aviso y el resto del HIS sigue funcionando con el inventario local.

## 7. Seguridad implementada

- Contraseñas con **BCrypt** y política de complejidad (8+ caracteres, mayúscula, número y símbolo).
- **Bloqueo** de la cuenta por 15 minutos tras 5 intentos fallidos.
- **Segundo factor (TOTP)** configurable por rol en *Parámetros* (compatible con Google/Microsoft Authenticator; la primera vez muestra un código QR). Viene apagado para que puedas probar sin celular.
- **RBAC dinámico**: cada petición se valida contra la matriz *rol × módulo*; el acceso denegado responde 403 y queda auditado.
- **Auditoría de solo inserción**: login, consulta de historia, firmas, dispensaciones, cambios de permisos, exportaciones…
- **Historia clínica inalterable** al firmar (huella SHA-256); las correcciones se hacen con notas aclaratorias.
- Protección **CSRF** en todos los formularios, cierre de sesión por inactividad (15 min) y cabeceras anti-*clickjacking*.

---

## 8. Estructura del proyecto

```
src/main/java/co/santahelena/his/
├── model/      Documentos MongoDB (Paciente, Cita, Atencion, Prescripcion, Cama, Factura…)
├── repo/       Repositorios Spring Data
├── service/    Reglas de negocio por módulo (HCE, farmacia, laboratorio, camas, facturación…)
│               y CumService: cliente de la API del INVIMA en datos.gov.co
├── security/   Login, bloqueo, MFA e interceptor de permisos
├── config/     Seguridad, web y carga de datos de demostración
├── util/       Reglas puras (triaje, FEFO, alertas), TOTP, hash, código de barras
└── web/        Controladores (un controlador por módulo/rol)
src/main/resources/
├── templates/  Vistas Thymeleaf por módulo + documentos imprimibles
└── static/     CSS y JavaScript de la interfaz
```

Colecciones principales en MongoDB: `usuarios`, `permisos`, `pacientes`, `citas`, `admisiones`, `triajes`, `signos_vitales`, `atenciones`, `prescripciones`, `medicamentos`, `lotes`, `movimientos`, `ordenes_lab`, `examenes_lab`, `camas`, `cuentas`, `facturas`, `paz_y_salvos`, `auditoria`, `alertas`, `catalogos`.

---

## 9. Problemas frecuentes

| Síntoma | Solución |
|---|---|
| `MongoTimeoutException` / `Connection refused` al arrancar | MongoDB no está corriendo. Inicia el servicio (Windows: *Servicios → MongoDB Server*) o `docker compose up -d`. |
| Error de autenticación con Atlas | Revisa usuario/clave de *Database Access* y que tu IP esté en *Network Access*. Si la clave tiene caracteres especiales, codifícalos (`@` → `%40`). |
| `Port 8080 already in use` | Cambia `server.port` en `application.properties` o cierra la otra aplicación. |
| *Catálogo INVIMA* dice que no pudo consultar | El servidor no tiene internet o datos.gov.co está caído. Prueba abrir <https://www.datos.gov.co/resource/i7cb-raxc.json?$limit=1> en el navegador. |
| No se ven los íconos | Los íconos y la fuente se cargan desde internet (Bootstrap Icons y Google Fonts); revisa la conexión. |
| Olvidé configurar el autenticador y quedé por fuera | Entra con `admin` y en *Usuarios* pulsa *MFA* sobre el usuario para reiniciarlo. Si el bloqueado es el admin, en Compass edita su documento en `usuarios` y pon `mfaConfigurado: false` o quita el rol de `rolesConMfa` en `configuracion`. |
| Quiero empezar de cero | Borra la base `his_santahelena` en Compass y reinicia la aplicación. |
