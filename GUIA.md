# Guía del backend — Concurso de jerseys

Esta guía explica **cómo funciona el backend** y **cómo trabajar en él**. Léela entera antes de tocar código: te ahorrará muchas horas.

---

## 1. Las piezas y cómo se conectan

```
 ┌────────────┐   HTTPS    ┌─────────┐   /         ┌──────────────────────┐
 │ Navegador  │ ─────────► │  Nginx  │ ──────────► │ Front (HTML/CSS/JS)  │
 │ (usuario)  │            │         │   /api/     ┌──────────────────────┐   JDBC   ┌────────────┐
 └────────────┘            │         │ ──────────► │ Spring Boot (Java)   │ ───────► │ PostgreSQL │
                           │         │   /imagenes/└──────────────────────┘          └────────────┘
                           │         │ ──────────►  Fotos (carpeta del FTP) ◄── el backend sube por FTP
                           └─────────┘
```

- **El front** son archivos estáticos: HTML, CSS y JS. El navegador los descarga y los ejecuta. El front **no** toca la base de datos: le pide todo al backend.
- **El backend** (este proyecto) es un programa Java que está siempre encendido, escuchando peticiones HTTP en el puerto 8080. Aplica las reglas del concurso y es **el único** que habla con la base de datos.
- **La base de datos** (PostgreSQL) guarda usuarios, jerseys, votos y fechas.
- **Nginx** (lo monta ASIR) es la puerta de entrada: reparte cada petición según su ruta.

> **Idea clave:** el front es "la cara" y el backend es "el cerebro". Todo lo que el usuario ve y toca es del front. Todo lo que hay que **comprobar** o **guardar** es del backend, aunque el front ya lo haya comprobado. Cualquiera puede saltarse el front y llamar a la API directamente con Postman.

---

## 2. Cómo se comunica el front con el back: HTTP + JSON

El front y el back hablan con **peticiones HTTP**. Cada petición tiene:

| Parte | Ejemplo | Para qué sirve |
| --- | --- | --- |
| Método | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | Qué quieres hacer (leer, crear, cambiar…) |
| Ruta | `/api/jerseys/mio` | Sobre qué recurso |
| Cabeceras | `Authorization: Bearer eyJ…` | Información extra (quién eres, formato…) |
| Cuerpo | `{"email":"ana@x.com","password":"…"}` | Los datos que envías (en JSON) |

Y el backend responde con un **código de estado** y, normalmente, un JSON:

| Código | Significado | Ejemplo en este proyecto |
| --- | --- | --- |
| 200 OK | Todo bien | Login correcto |
| 201 Created | Se ha creado algo | Jersey subido, votos guardados |
| 302 Found | Redirección | Enlace de verificación del correo |
| 400 Bad Request | Datos incorrectos | Falta una casilla, foto no válida |
| 401 Unauthorized | No has iniciado sesión | Falta el token |
| 403 Forbidden | Sesión iniciada pero no puedes | Un usuario pide el ranking; fase incorrecta |
| 404 Not Found | No existe | No has subido jersey |
| 409 Conflict | Choca con algo que ya existe | Ya has votado, ya has subido |
| 429 Too Many Requests | Demasiadas peticiones seguidas | Muchos intentos de login desde la misma IP |

**Todos los errores** tienen el mismo formato, así el front puede mostrar el mensaje directamente:

```json
{ "codigo": "YA_HAS_VOTADO", "mensaje": "Ya has votado. Los votos no se pueden cambiar.", "campos": null }
```

### Ejemplos de código para el front (JavaScript)

**Login y guardar el token:**

```js
const respuesta = await fetch("/api/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email, password }),
});
const datos = await respuesta.json();
if (!respuesta.ok) {
  alert(datos.mensaje);              // p. ej. "Email o contraseña incorrectos"
} else {
  sessionStorage.setItem("token", datos.token);
  sessionStorage.setItem("rol", datos.usuario.rol);   // USUARIO | PROFESOR | ADMIN
}
```

**Llamar a una ruta protegida** (hay que mandar el token en cada petición):

```js
const r = await fetch("/api/jerseys/mio", {
  headers: { Authorization: "Bearer " + sessionStorage.getItem("token") },
});
```

**Subir la foto** (no es JSON: es un formulario con archivo):

```js
const fd = new FormData();
fd.append("foto", inputFile.files[0]);   // el campo DEBE llamarse "foto"
const r = await fetch("/api/jerseys", {
  method: "POST",
  headers: { Authorization: "Bearer " + sessionStorage.getItem("token") },
  body: fd,                              // ¡sin Content-Type! el navegador lo pone solo
});
```

**Registro con CAPTCHA** (Cloudflare Turnstile). El widget genera un token que hay que mandar en `captchaToken`:

```html
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
<form id="registro">
  <!-- ... email, nombre, password, casillas ... -->
  <div class="cf-turnstile" data-sitekey="LA_SITEKEY_PUBLICA"></div>
  <button>Registrarme</button>
</form>
```

```js
const captchaToken = document.querySelector("[name='cf-turnstile-response']").value;
const r = await fetch("/api/auth/registro", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email, nombre, password, aceptaPrivacidad, aceptaConsentimiento, mayorDe14, captchaToken }),
});
if (!r.ok) turnstile.reset();   // el token es de UN solo uso: si el registro falla, hay que generar otro
```

Para probar en local, usad la sitekey de prueba `1x00000000000000000000AA` (siempre aprueba). En el backend el CAPTCHA está desactivado en el perfil `dev`; para probarlo de verdad, poned `app.captcha.activado=true` en `application-dev.properties`.

**Votar** (los 3 de golpe: 3, 2 y 1 puntos a jerseys distintos):

```js
await fetch("/api/votos", {
  method: "POST",
  headers: { "Content-Type": "application/json", Authorization: "Bearer " + token },
  body: JSON.stringify({ votos: [
    { jerseyId: 12, puntos: 3 }, { jerseyId: 3, puntos: 2 }, { jerseyId: 7, puntos: 1 },
  ]}),
});
```

### El contrato de la API: Swagger

Con el backend arrancado, abre **http://localhost:8080/swagger-ui.html**. Ahí están **todas las rutas**, qué JSON esperan y qué devuelven, y puedes probarlas desde el navegador:

1. Ejecuta `POST /api/auth/login` y copia el `token` de la respuesta.
2. Pulsa el botón **Authorize** (arriba a la derecha) y pega el token.
3. Ya puedes probar las rutas protegidas.

Pasadle este enlace al grupo del front: es la documentación oficial y siempre está al día, porque se genera a partir del código.

### CORS (solo en desarrollo)

Por seguridad, el navegador bloquea que una página de `localhost:5500` (Live Server) llame a `localhost:8080`, porque son **orígenes distintos**. El perfil `dev` ya permite esos dos orígenes. En producción no hace falta: Nginx sirve el front y la API en el mismo dominio.

---

## 3. El recorrido de una petición dentro del backend

Cuando llega `POST /api/votos`, pasa por estas capas, en orden:

```
Petición HTTP
   │
   ▼
FiltroJwt ............ ¿trae "Authorization: Bearer <token>"? → ¿quién es el usuario?
   │
   ▼
SeguridadConfig ...... ¿esta ruta es pública? ¿necesita login? ¿necesita un rol?  (si no: 401/403)
   │
   ▼
VotoController ....... convierte el JSON en un objeto Java y lo valida (@Valid)
   │
   ▼
VotoService .......... aplica las REGLAS: fase correcta, no votarse, puntos 6..1...
   │
   ▼
VotoRepository ....... guarda en la base de datos
   │
   ▼
PostgreSQL ........... las restricciones (UNIQUE, CHECK) son la última barrera
```

Si algo falla en cualquier punto, se lanza una excepción y **ManejadorErrores** la convierte en el JSON de error.

| Capa | Anotación | Qué hace | Qué NO hace |
| --- | --- | --- | --- |
| Controlador | `@RestController` | Recibe HTTP, valida el formato, devuelve JSON | Reglas de negocio |
| Servicio | `@Service` | Reglas de negocio, transacciones | Saber de HTTP |
| Repositorio | `interface … extends JpaRepository` | Leer y escribir en la BD | Reglas de negocio |
| Entidad | `@Entity` | Representa una fila de una tabla | — |
| DTO | `record` | La forma del JSON que entra y sale | — |

### Inyección de dependencias

Ninguna clase hace `new` de otra. Cada una pide lo que necesita en su **constructor**, y Spring se lo pasa ya creado:

```java
@Service
public class VotoService {
    public VotoService(VotoRepository votos, FaseService fases, ...) { ... }
}
```

Spring crea **un único objeto** de cada `@Service`, `@Component`, `@Repository`… (se llaman *beans*) y los conecta entre sí al arrancar.

---

## 4. Cómo se conecta el backend con la base de datos

Son cuatro capas, de más baja a más alta:

1. **Driver JDBC** (`org.postgresql:postgresql` en el `pom.xml`): el "traductor" que sabe hablar el protocolo de PostgreSQL. Es lo mismo que usáis en Acceso a Datos con `DriverManager.getConnection(...)`.
2. **DataSource / pool de conexiones** (HikariCP, viene incluido): abrir una conexión es lento, así que Spring mantiene ~10 abiertas y las reutiliza. Se configura con `spring.datasource.url`, `username` y `password`.
3. **JPA / Hibernate**: en lugar de escribir SQL y leer `ResultSet` a mano, anotas una clase con `@Entity` y Hibernate convierte filas en objetos y viceversa.
4. **Spring Data JPA**: escribes solo la **interfaz** del repositorio y Spring genera el código. Los métodos se traducen a SQL por su nombre:

```java
Optional<Usuario> findByEmailNormalizado(String email);
// → SELECT * FROM usuarios WHERE email_normalizado = ?
```

### Flyway: el esquema de la BD versionado

Las tablas **no** las crea Hibernate: las crean los scripts SQL de `src/main/resources/db/migration/`.

- Al arrancar, Flyway mira qué scripts ya se aplicaron (tabla `flyway_schema_history`) y ejecuta los nuevos, en orden: `V1__…`, `V2__…`, `V3__…`.
- Después, Hibernate **valida** (`ddl-auto=validate`) que las entidades coinciden con las tablas. Si no coinciden, la aplicación no arranca y te dice qué columna falla.

> ⚠️ **Nunca modifiques un script que ya se ha ejecutado** (en tu máquina, en la de un compañero o en el servidor). Si necesitas cambiar una tabla, crea uno nuevo: `V2__anadir_titulo_a_jerseys.sql`. Así la BD de todos evoluciona igual.

### Transacciones (`@Transactional`)

Un método marcado con `@Transactional` es **todo o nada**: si falla a mitad, se deshace todo lo que había hecho en la BD. Además, dentro de una transacción, los cambios en una entidad cargada se guardan solos al terminar (`usuario.setRol(...)` sin llamar a `save()`).

---

## 5. Seguridad: contraseñas, JWT y roles

- **Contraseñas:** se guarda solo su hash **bcrypt**, nunca la contraseña. Ni siquiera el admin puede verlas.
- **JWT:** al hacer login, el backend devuelve un token firmado con una clave secreta. El front lo manda en cada petición (`Authorization: Bearer …`). Si alguien modifica el token, la firma deja de cuadrar y se rechaza. Caduca a las 12 horas.
- **Roles:** las reglas están en `SeguridadConfig`:
  - `/api/admin/**` → solo `ADMIN`
  - `/api/ranking` → `PROFESOR` o `ADMIN`
  - el resto de `/api/**` → cualquier usuario logueado, salvo las rutas públicas (registro, login, fase).
- **IPs:** se guardan como HMAC (un hash con clave secreta), no en claro.
- **Secretos:** las claves y contraseñas van en **variables de entorno**, nunca en el código ni en Git.

---

## 6. Estructura del proyecto

```
src/main/java/com/jerseys/backend/
├── JerseysBackendApplication.java   ← main(): arranca todo
├── auth/        registro, verificación de email, login
├── usuario/     entidad Usuario, roles, normalización de emails
├── seguridad/   JWT, FiltroJwt, SeguridadConfig (reglas de acceso)
├── fase/        fechas del concurso y cálculo de la fase actual
├── jersey/      subir jersey, listar, eliminar
├── imagen/      procesar fotos, validadores (caras, NSFW), almacén local/FTP
├── voto/        votar y reglas de votación
├── ranking/     clasificación (solo profesores y admin)
├── admin/       panel de administración
├── correo/      envío de correos
├── comun/       errores, hash, utilidades compartidas
└── config/      configuración (propiedades, Swagger, admin inicial, reloj)

src/main/resources/
├── application.properties       ← configuración común (lee variables de entorno)
├── application-dev.properties   ← valores para tu ordenador
└── db/migration/                ← scripts de Flyway (V1, V2...)

src/test/java/...                ← tests (se ejecutan con .\mvnw.cmd test)
```

---

## 7. Arrancar el backend en tu ordenador

### Requisitos

- **Java 21 o superior** (`java -version` en una terminal).
- **PostgreSQL**: elige una de las dos opciones.
  - **Instalarlo:** descarga el instalador de postgresql.org para Windows. Durante la instalación recuerda la contraseña del usuario `postgres`. Después, abre **pgAdmin** (o `psql`) y ejecuta:
    ```sql
    CREATE USER jerseys WITH PASSWORD 'jerseys';
    CREATE DATABASE jerseys OWNER jerseys;
    ```
  - **Con Docker:** `docker compose up -d` en esta carpeta, y ya está.
- **No hace falta instalar Maven:** el proyecto trae el *Maven Wrapper* (`mvnw.cmd`), que lo descarga solo.

### Arrancar

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

La primera vez tarda un poco (descarga las dependencias). Cuando veas `Started JerseysBackendApplication`, ya funciona:

- API: http://localhost:8080/api/fase
- Swagger: http://localhost:8080/swagger-ui.html
- Admin de desarrollo: `admin@jerseys.local` / `admin12345`

### Probar el flujo completo en Swagger

1. `POST /api/auth/registro` con tu email.
2. El correo **no se envía** en desarrollo: aparece en la **consola** del backend. Copia el enlace y ábrelo en el navegador para verificar la cuenta. Te redirigirá a `/verificacion.html`, que dará 404 hasta que el front haga esa página, pero la cuenta ya queda activada.
3. Entra como admin y fija las fechas con `PUT /api/admin/configuracion` (por ejemplo, subida abierta ahora).
4. Entra con tu usuario, sube una foto, cambia las fechas para abrir la votación, vota…

### Ejecutar los tests

```powershell
.\mvnw.cmd test
```

Los tests arrancan un **PostgreSQL embebido** automáticamente (no necesitas tenerlo instalado) y prueban el concurso de principio a fin. **Ejecutadlos antes de cada Pull Request.**

---

## 8. Cómo añadir una funcionalidad nueva (receta)

Ejemplo: "los jerseys tienen un título".

1. **BD:** crea `V2__titulo_jersey.sql` con `ALTER TABLE jerseys ADD COLUMN titulo VARCHAR(60);`
2. **Entidad:** añade el campo `titulo` a `Jersey.java` con su `@Column`.
3. **DTO:** añádelo a los `record` de `JerseyDtos` donde deba entrar o salir.
4. **Servicio:** aplica las reglas (longitud, palabras prohibidas…).
5. **Controlador:** recibe el nuevo dato (en este caso, un `@RequestPart` o un `@RequestParam` más).
6. **Test:** amplía `FlujoCompletoTest` o crea uno nuevo.
7. `.\mvnw.cmd test` en verde → Pull Request.

---

## 9. Normas del equipo

- **Git:** cada tarea en su rama (`feature/detector-caras`). Nada entra en `main` sin un Pull Request revisado por otra persona y con los tests en verde.
- **Flyway:** nunca modifiques una migración ya aplicada. Crea una nueva.
- **Secretos:** nunca en el código ni en Git. Van en variables de entorno (ver `.env.example`).
- **Errores:** lanza `ApiException` con un código en MAYÚSCULAS y un mensaje claro para el usuario. Nada de `try/catch` en los controladores.
- **Reglas en el backend:** cualquier comprobación que haga el front, el backend tiene que hacerla también.

---

## 10. Qué falta por hacer (reparto sugerido)

| Tarea | Dónde | Notas |
| --- | --- | --- |
| ~~Detección de caras con OpenCV~~ ✅ | `imagen/DetectorCaras.java` | Hecho. OpenCV + modelo YuNet (`resources/modelos/`); error `FOTO_CON_CARA` (400) |
| Detección de contenido obsceno | `imagen/DetectorContenidoInapropiado.java` | ONNX Runtime + modelo NSFW |
| ~~CAPTCHA en el registro~~ ✅ | `auth/VerificadorCaptcha` | Hecho. Campo `captchaToken`; errores `CAPTCHA_OBLIGATORIO`/`CAPTCHA_INVALIDO` (400) y `CAPTCHA_NO_DISPONIBLE` (503). Falta crear el widget en Cloudflare |
| ~~Bloquear correos temporales~~ ✅ | `auth/DetectorCorreoTemporal` | Hecho. Lista en `resources/correos-temporales.txt`; error `EMAIL_TEMPORAL` (400) |
| ~~Límite de peticiones por IP~~ ✅ | `seguridad/LimitadorPeticiones` | Hecho. Login, registro y reenvío del correo; error `DEMASIADAS_PETICIONES` (429). Límites en `app.limites.*` |
| ~~Panel de sospechosos~~ ✅ | `admin/AnalizadorSospechosos` | Hecho. `GET /api/admin/sospechosos`: ráfagas de registros/votos por IP, mismo favorito y papeletas idénticas. Falta la pantalla en el front |
| Almacén SFTP | nuevo `imagen/AlmacenSftp.java` | Si ASIR monta SFTP en vez de FTP |
| ~~Decidir el sistema de votación~~ ✅ | `voto/VotoService` | Hecho. Sistema 3-2-1 (migración `V2__votacion_3_2_1.sql`) |
| Despliegue | con ASIR | Java 21, servicio systemd, variables de `.env.example` |

### Cambios respecto al documento de diseño

- **Verificación de email:** `GET /api/auth/verificar?token=…` activa la cuenta y **redirige** a `/verificacion.html?resultado=ok|caducado|invalido`. El front debe crear esa página.
- **Eliminar jersey:** es `POST /api/admin/jerseys/{id}/eliminar` con `{"motivo": "…"}` (no `DELETE`), porque lleva un cuerpo con el motivo.
- **Fase `PREPARACION`:** nueva, para antes de que empiece la subida o mientras el admin no ha puesto fechas.
- **Sistema de votación 3-2-1:** cada usuario da 3 puntos a un jersey, 2 a otro y 1 a otro (antes era 6-5-4-3-2-1). Desempate en el ranking: más votos de 3, luego de 2, luego de 1.
- **Sin mínimo de participantes:** si hay menos de 3 jerseys ajenos, se reparten solo las puntuaciones más altas (con 2 jerseys: 3 y 2; con 1: solo el 3).
