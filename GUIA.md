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
| Ruta | `/api/jerseys/mios` | Sobre qué recurso |
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
| 404 Not Found | No existe | Un jersey que no existe |
| 409 Conflict | Choca con algo que ya existe | Ya has votado, ya tienes un jersey |
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
  sessionStorage.setItem("rol", datos.usuario.rol);   // USUARIO | JURADO | ADMIN
}
```

**Llamar a una ruta protegida** (hay que mandar el token en cada petición):

```js
const r = await fetch("/api/jerseys/mios", {
  headers: { Authorization: "Bearer " + sessionStorage.getItem("token") },
});
const { maximo, activos, jerseys } = await r.json();
// p. ej. "Te quedan 2 de 3" → maximo - activos. Los eliminados también vienen (estado ELIMINADO + motivo).
```

**Subir un jersey** (no es JSON: es un formulario con archivos). Foto de delante obligatoria y de detrás opcional:

```js
const fd = new FormData();
fd.append("foto", inputDelante.files[0]);            // delante: el campo DEBE llamarse "foto"
if (inputDetras.files[0]) {
  fd.append("fotoTrasera", inputDetras.files[0]);    // detrás (opcional): "fotoTrasera"
}
const r = await fetch("/api/jerseys", {
  method: "POST",
  headers: { Authorization: "Bearer " + sessionStorage.getItem("token") },
  body: fd,                              // ¡sin Content-Type! el navegador lo pone solo
});
// Si la de detrás no vale, el mensaje empieza por "Foto de detrás: ..." y no se guarda ninguna de las dos.
```

En las respuestas, `imagenUrl` es la foto de delante e `imagenTraseraUrl` la de detrás (`null` si no hay).

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
  body: JSON.stringify({ email, nombre, password, aceptaPrivacidad, aceptaConsentimiento, mayorDe18, captchaToken }),
});
if (!r.ok) turnstile.reset();   // el token es de UN solo uso: si el registro falla, hay que generar otro
```

Para probar en local, usad la sitekey de prueba `1x00000000000000000000AA` (siempre aprueba). En el backend el CAPTCHA está desactivado en el perfil `dev`; para probarlo de verdad, poned `app.captcha.activado=true` en `application-dev.properties`.

**¿Has olvidado tu contraseña?** Son dos pasos y **dos páginas del front**:

```js
// 1) En el login, un enlace "¿Has olvidado tu contraseña?" lleva a un formulario que pide el email.
//    Siempre responde lo mismo, exista o no la cuenta (para no revelar quién está registrado).
await fetch("/api/auth/olvide-password", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email }),
});

// 2) Al usuario le llega un correo con un enlace a la página del front
//    /restablecer-password.html?token=XXXX  (dura 1 hora y solo vale una vez).
//    Esa página pide la contraseña nueva (8 a 72 caracteres) y la manda junto con el token:
const token = new URLSearchParams(location.search).get("token");
const r = await fetch("/api/auth/restablecer-password", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ token, password: nuevaPassword }),
});
const datos = await r.json();
alert(datos.mensaje);   // OK → "Contraseña cambiada..." y llevarle al login
// Errores: ENLACE_NO_VALIDO (no existe o ya se usó), ENLACE_CADUCADO (más de 1 hora), DATOS_INVALIDOS (contraseña corta)
```

Al cambiar la contraseña **se cierran todas las sesiones abiertas** de esa cuenta: los tokens anteriores dan `401`, también en otros navegadores. Si la cuenta no estaba verificada, queda verificada (el enlace le llegó a su correo).

**Mi cuenta** (con la sesión iniciada; las dos piden la contraseña actual):

```js
const h = { "Content-Type": "application/json", Authorization: "Bearer " + token };

// Cambiar la contraseña. Cierra la sesión en los demás navegadores y devuelve un token NUEVO:
// hay que guardarlo en lugar del anterior, o la siguiente petición dará 401.
const r = await fetch("/api/cuenta/cambiar-password", {
  method: "POST", headers: h, body: JSON.stringify({ passwordActual, passwordNueva }),
});
if (r.ok) sessionStorage.setItem("token", (await r.json()).token);
// Error PASSWORD_INCORRECTA (400) si la actual no es correcta

// Borrar la cuenta (pedid confirmación antes: no se puede deshacer). Después, borrad el token y al inicio.
await fetch("/api/cuenta/borrar", { method: "POST", headers: h, body: JSON.stringify({ password }) });
```

Qué pasa al borrar la cuenta:
- **Siempre** se borran los datos personales (email, nombre, contraseña, IP) y se cierran las sesiones. Con ese email se puede crear otra cuenta.
- **Hasta que acaba la votación:** se retira su jersey (y se borran las fotos) y se borran los votos que había dado. A quien hubiera votado su jersey le llega un correo: ese voto ya no cuenta.
- **Después de la votación:** los premios ya se están decidiendo, así que el jersey y los votos se quedan (sin nombre ni IP) para que los ganadores no cambien por sorpresa. Aparece como "Cuenta borrada".
- Un **admin** no puede borrar su cuenta (`ADMIN_NO_PUEDE_BORRARSE`): os podríais quedar sin ninguno. Que otro admin le quite el rol antes.

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

**Volver a votar.** Si a alguien le anulan un voto (lo anula el admin, o se retira el jersey que votó), le llega un correo y, **mientras dure la votación, puede volver a dar esos puntos** a otro jersey. Sus otros votos siguen contando y no se tocan. Para la pantalla de votar, preguntad siempre qué puntos le quedan:

```js
const h = { Authorization: "Bearer " + token };
// [3, 2, 1] si aún no ha votado · [3] si le anularon el de 3 puntos · [] si ya ha votado
const { puntos } = await (await fetch("/api/votos/pendientes", { headers: h })).json();
// Sus votos; anulado: true = ya no cuenta (enseñadlo tachado, por ejemplo)
const mios = await (await fetch("/api/votos/mios", { headers: h })).json();
```

Si `puntos` está vacío, enseñad "Ya has votado". Si no, que reparta **exactamente esos puntos** con el mismo `POST /api/votos` de arriba, a jerseys que no haya votado ya. Si elige el jersey cuyo voto le anularon: `400 JERSEY_YA_VOTADO`.

**Jurado** (solo rol `JURADO`). Votan en el voto popular de arriba como todo el mundo. Después, en la fase `DECISION_JURADO`, se reúnen **en persona**, lo hablan y uno de ellos marca en la web el ganador de cada una de las 4 categorías:

```js
const h = { "Content-Type": "application/json", Authorization: "Bearer " + token };

// Las 4 categorías y el ganador elegido en cada una (ganador: null si aún no):
// [{ categoria: "DIVERTIDO", nombre: "El más divertido", descripcion, ganador: { id, imagenUrl, ... } | null }, ...]
const categorias = await (await fetch("/api/jurado/categorias", { headers: h })).json();

// Los jerseys entre los que elegir. El ganador del voto popular NO sale (un jersey, un premio).
// [{ id, imagenUrl, imagenTraseraUrl, esPropio, ganadorDe }]
//   esPropio: true  → es el tuyo: no puedes marcarlo tú (que lo marque otro del jurado)
//   ganadorDe: "ORIGINAL" → ya ha ganado esa categoría y no puede ganar otra (grísalo); null si ninguna
const jerseys = await (await fetch("/api/jurado/jerseys", { headers: h })).json();

// Marcar el ganador de una categoría (si ya había uno, lo sustituye)
await fetch("/api/jurado/ganadores/DIVERTIDO", {   // DIVERTIDO | ORIGINAL | NAVIDENO | EXTRAVAGANTE
  method: "PUT", headers: h, body: JSON.stringify({ jerseyId: 12 }),
});

// Quitar el ganador de una categoría (por si se equivocan y quieren ese jersey para otra)
await fetch("/api/jurado/ganadores/DIVERTIDO", { method: "DELETE", headers: h });
```

Errores posibles al marcar: `YA_TIENE_PREMIO` (409, ya ganó el popular u otra categoría), `JERSEY_PROPIO` (400), `FASE_INCORRECTA` (403, fuera de la fase del jurado).

**Resultados** (públicos, sin iniciar sesión, desde que empieza la fase `RESULTADOS`; antes, `403 FASE_INCORRECTA`). Solo dicen quién ha ganado, nunca los puntos:

```js
const { premios } = await (await fetch("/api/resultados")).json();
// premios[0] es el popular y luego las 4 del jurado:
// [{ categoria: "POPULAR", nombre: "El favorito del público", descripcion,
//    ganador: { jerseyId, imagenUrl, imagenTraseraUrl, autor } | null }, ...]   (null = premio desierto)
```

El admin puede verlos antes de publicarlos en `GET /api/admin/resultados`, y el ranking con los puntos en `GET /api/ranking` (solo él).

### Fases y calendario del concurso

`GET /api/fase` (público) dice en qué fase estamos, para que el front sepa qué pantalla enseñar. Todas las horas son las **12:00 del mediodía, hora de España**:

| Fase | Desde | Hasta | Qué se puede hacer |
| --- | --- | --- | --- |
| `PREPARACION` | — | 6 dic | Registrarse |
| `SUBIDA` | 6 dic | 10 dic | Subir el jersey. El admin puede retirar fotos |
| `ESPERA` | — | — | Solo si se deja hueco entre subida y votación (este año no) |
| `VOTACION` | 10 dic | 13 dic | Voto popular (también el jurado). El admin puede anular votos |
| `DECISION_JURADO` | 13 dic | 14 dic | El jurado marca el ganador de cada categoría |
| `ESPERA_RESULTADOS` | 14 dic | 16 dic | Nada: todo decidido, sin publicar |
| `RESULTADOS` | 16 dic | — | `GET /api/resultados` para todos |

El admin las fija con `PUT /api/admin/configuracion`. Las fechas van **en UTC** (la `Z` del final), y en diciembre España va una hora por delante, así que **las 12:00 de España son las `11:00Z`**:

```json
{
  "inicioSubida":          "2026-12-06T11:00:00Z",
  "finSubida":             "2026-12-10T11:00:00Z",
  "inicioVotacion":        "2026-12-10T11:00:00Z",
  "finVotacion":           "2026-12-13T11:00:00Z",
  "finJurado":             "2026-12-14T11:00:00Z",
  "publicacionResultados": "2026-12-16T11:00:00Z"
}
```

La decisión del jurado empieza justo cuando acaba la votación (`finVotacion`). **Ojo, admin:** los votos fraudulentos hay que anularlos **antes** de que acabe la votación. Después ya no se puede, porque cambiaría el ganador popular mientras el jurado está eligiendo sin contar con él.

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
VotoService .......... aplica las REGLAS: fase correcta, no votarse, puntos 3-2-1...
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
  - `/api/ranking` → solo `ADMIN`: es el único que ve los votos y los puntos
  - `/api/jurado/**` → solo `JURADO`
  - el resto de `/api/**` → cualquier usuario logueado, salvo las rutas públicas (registro, login, fase y resultados).
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
├── voto/        voto popular 3-2-1 y sus reglas
├── ranking/     clasificación del voto popular con puntos (solo admin)
├── premio/      categorías, ganadores que elige el jurado y resultados
├── cuenta/      cambiar la contraseña y borrar la cuenta
├── aviso/       avisos por correo a todos al cambiar de fase
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
2. En desarrollo el correo **no se envía** (salvo que configures uno, ver "Correo" más abajo): aparece en la **consola** del backend. Copia el enlace y ábrelo en el navegador para verificar la cuenta. Te redirigirá a `/verificacion.html`, que dará 404 hasta que el front haga esa página, pero la cuenta ya queda activada.
3. Entra como admin y fija las fechas con `PUT /api/admin/configuracion` (las 6; por ejemplo, subida abierta ahora). Ver "Fases y calendario del concurso".
4. Entra con tu usuario, sube una foto, cambia las fechas para abrir la votación, vota… y luego la fase del jurado y los resultados.

### Correo

El backend envía correos para **activar la cuenta** y para avisar si el admin **retira un jersey**. Usa SMTP, que es el "idioma" estándar del correo, así que vale cualquier proveedor: solo cambian las variables de entorno.

- **En desarrollo** (perfil `dev`), si no defines `MAIL_HOST`, los correos se escriben en la consola.
- **En producción es obligatorio:** sin `MAIL_HOST`, la aplicación **no arranca**. Es a propósito: sin correo nadie podría activar su cuenta.
- Los correos se envían **en segundo plano** (el usuario no espera) y **solo cuando la base de datos ha guardado los cambios**. Si el envío falla, se apunta en el log y el usuario puede pedir otro con `POST /api/auth/reenviar-verificacion`.

**Opción elegida: el correo del centro (`@victoriafp.com`).** Es la que usamos. El servidor `mail.victoriafp.com` está autorizado en el SPF del dominio, así que los correos llegan como legítimos y no suelen ir a spam.

```
MAIL_HOST=mail.victoriafp.com
MAIL_PORT=587                        # STARTTLS. No uséis el 465: necesita otra configuración
MAIL_USUARIO=la-cuenta@victoriafp.com
MAIL_PASSWORD=la-contraseña-de-ese-buzón
MAIL_REMITENTE=                      # vacío: se envía como MAIL_USUARIO
```

- Mejor una cuenta **dedicada al concurso** que el buzón personal de alguien: si esa persona cambia su contraseña, el correo del concurso deja de funcionar.
- Preguntad a informática del centro **cuántos correos al día** deja enviar el servidor. El primer día puede haber muchos registros a la vez.

Las opciones A y B quedan como alternativa por si el correo del centro diera problemas.

**Opción A: Gmail.** Es la más sencilla, y el límite es de unos 500 correos al día.
1. Cread una cuenta de Gmail **solo para el concurso**, no la personal de nadie.
2. Activad la **verificación en dos pasos** de esa cuenta.
3. Id a https://myaccount.google.com/apppasswords y cread una **contraseña de aplicación**: son 16 letras, y se ponen sin espacios.
4. Variables:
   ```
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USUARIO=la-cuenta@gmail.com
   MAIL_PASSWORD=la-contraseña-de-aplicacion
   MAIL_REMITENTE=                      # vacío: Gmail obliga a usar la misma cuenta
   ```

**Opción B: Brevo** (brevo.com). Plan gratis de 300 correos al día.
1. Cread una cuenta y **verificad el email remitente** (Senders).
2. En **SMTP & API** están el *login* SMTP y la *clave* SMTP.
3. Variables: `MAIL_HOST=smtp-relay.brevo.com`, `MAIL_PORT=587`, `MAIL_USUARIO=<login SMTP>`, `MAIL_PASSWORD=<clave SMTP>` y `MAIL_REMITENTE=<el email verificado>`.

**Qué correos manda el backend:**

| Cuándo | A quién |
| --- | --- |
| Al registrarse (enlace para activar la cuenta) | Quien se registra |
| "¿Has olvidado tu contraseña?" | Quien lo pide |
| Un admin retira tu jersey (con el motivo) | El autor |
| Uno de tus votos deja de contar: lo anula el admin, o se retira el jersey que votaste. Puede volver a dar esos puntos | Quien votó |
| **Empieza la votación** | Todos* |
| **Se cierra la votación** | Todos* |
| **Se publican los ganadores** (a los ganadores, con su premio) | Todos* |

\* "Todos" = las cuentas verificadas y activas. Estos tres avisos los manda solo el backend: cada minuto mira en qué fase estamos y, si ha empezado una nueva, manda el aviso **una vez** (apunta en la BD que ya lo ha mandado). Si el admin cambia las fechas y una fase vuelve a quedar en el futuro, su aviso se podrá mandar otra vez.

> ⚠️ **Límite del servidor de correo.** Los servidores de correo suelen limitar cuántos correos se pueden mandar por hora, y si se pasa, rechazan también los de verificación. Por eso los avisos a todos salen **uno a uno, con 2 segundos de pausa** (como mucho 1.800 por hora): con 300 participantes tardan unos 10 minutos. Preguntad al responsable del correo del centro cuál es su límite y, si es más bajo, subid `CORREO_PAUSA_MASIVOS` (p. ej. `10s` = 360 por hora).

**Comprobarlo:** arranca el backend con esas variables, entra como admin en Swagger y ejecuta `POST /api/admin/correo/prueba`. Te llegará un correo a tu email de admin. Si falla, la respuesta trae el error que da el servidor de correo (contraseña mal puesta, puerto bloqueado…).

Para probarlo **en tu ordenador** con un correo de verdad, define las variables en la misma terminal antes de arrancar. La contraseña se pide con `Read-Host`: así **no se ve en pantalla ni se guarda en el historial** de PowerShell (si la escribieras en el comando, quedaría guardada en texto plano en tu PC):

```powershell
$env:MAIL_HOST = "mail.victoriafp.com"
$env:MAIL_USUARIO = "la-cuenta@victoriafp.com"
$env:MAIL_PASSWORD = [System.Net.NetworkCredential]::new("", (Read-Host "Contraseña del correo" -AsSecureString)).Password
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Las variables solo duran mientras esa terminal esté abierta. Al cerrarla, desaparecen.

> ⚠️ La contraseña de aplicación es un secreto: **nunca** en el código ni en Git. Solo en el `.env` del servidor.

### Ejecutar los tests

```powershell
.\mvnw.cmd test
```

Los tests arrancan un **PostgreSQL embebido** automáticamente (no necesitas tenerlo instalado) y prueban el concurso de principio a fin. **Ejecutadlos antes de cada Pull Request.**

### Tests automáticos en GitHub

Además, **GitHub los ejecuta solo** en cada Pull Request y cada vez que se fusiona algo en `main` (archivo `.github/workflows/tests.yml`). En el PR aparece:

- ✅ **All checks have passed**: todos los tests pasan, se puede fusionar.
- ❌ **Some checks were not successful**: algo se ha roto. Pulsa **Details** para ver qué test falla y por qué; el detalle de cada test está en **Summary → Artifacts → informes-de-tests**. Arreglad el fallo y subid otro commit a la misma rama: los tests se vuelven a ejecutar solos.

Tarda unos 3-5 minutos. Recomendación: en GitHub, **Settings → Branches → Add branch ruleset** para `main`, con "Require status checks to pass" y el check **Tests del backend**: así nadie puede fusionar un PR con los tests en rojo.

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
| ~~Detección de contenido obsceno~~ ✅ | `imagen/DetectorContenidoInapropiado.java` | Hecho. Modelo open_nsfw de Yahoo con OpenCV DNN (umbral 0.8); error `FOTO_INAPROPIADA` (400) |
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
- **Fases nuevas:** `PREPARACION` (antes de que empiece la subida o mientras el admin no ha puesto fechas), `DECISION_JURADO`, `ESPERA_RESULTADOS` y `RESULTADOS` (sustituye a `CERRADO`). Calendario: subida del 6 al 10 de diciembre, votación del 10 al 13, jurado del 13 al 14 y resultados el 16, siempre a las 12:00. Dos fechas nuevas en la configuración: `finJurado` y `publicacionResultados` (migración `V6__jurado_elige_ganadores.sql`).
- **Sistema de votación 3-2-1:** cada usuario da 3 puntos a un jersey, 2 a otro y 1 a otro (antes era 6-5-4-3-2-1). Desempate en el ranking: más votos de 3, luego de 2, luego de 1.
- **Sin mínimo de participantes:** si hay menos de 3 jerseys ajenos, se reparten solo las puntuaciones más altas (con 2 jerseys: 3 y 2; con 1: solo el 3).
- **Volver a votar si te anulan un voto:** durante la votación, se pueden volver a dar los puntos anulados a otro jersey (`GET /api/votos/pendientes` dice cuáles). Los votos anulados se quedan en la BD para que el admin los vea; los índices únicos de `votos` solo cuentan los no anulados (migración `V9__volver_a_votar.sql`).
- **Un jersey por persona** (se probó con 3 y al final se quedó en 1; cada jersey con una o dos fotos). Los eliminados por el admin no cuentan: si te retiran el tuyo, puedes subir otro. Si ya tienes uno: `409 LIMITE_DE_JERSEYS`. El límite es `JerseyService.MAX_JERSEYS_POR_USUARIO`. `GET /api/jerseys/mios` devuelve `{ maximo, activos, jerseys: [...] }`: el activo y los eliminados, con su motivo (lista vacía si no hay ninguno). La migración `V3__tres_jerseys_por_usuario.sql` quitó el índice único de la BD; ahora el límite lo controla `JerseyService`, que bloquea al usuario mientras sube para que un doble clic no cuele dos.
- **Rol `JURADO` y 5 premios** (bases, apartado 5). `POPULAR` lo decide el voto popular, en el que el jurado vota como uno más. `DIVERTIDO`, `ORIGINAL`, `NAVIDENO` y `EXTRAVAGANTE` los decide el jurado reunido en persona: no votan con puntos, marcan directamente el ganador de cada categoría (`/api/jurado/**`, ver "Jurado" en la sección 2). Al principio el jurado votaba 3-2-1 en cada categoría (migración V4); se cambió en V6, que borra esos votos y la columna `votos.categoria` y crea la tabla `ganadores_categoria`. El admin nombra jurado con `PATCH /api/admin/usuarios/{id}/rol` `{"rol":"JURADO"}`.
- **Un jersey, un premio:** el ganador popular no aparece para el jurado y un jersey no puede ganar dos categorías (`409 YA_TIENE_PREMIO`; la BD también lo impide con `UNIQUE (jersey_id)`). Quien marca no puede elegir su propio jersey (`JERSEY_PROPIO`).
- **Sin rol `PROFESOR`:** solo el `ADMIN` ve los votos y el ranking. Los que fueran profesores pasan a `USUARIO` (migración V6). Los ganadores, sin puntos, se publican para todos en `GET /api/resultados` a partir del 16.
- **Dos fotos por jersey:** delante (`foto`, obligatoria) y detrás (`fotoTrasera`, opcional). Las dos pasan las mismas comprobaciones (formato, caras, contenido inapropiado); si una falla no se guarda ninguna. Las respuestas traen `imagenTraseraUrl` (`null` si no hay). Columna `jerseys.imagen_trasera_nombre` (migración `V5__foto_trasera_y_mayor_18.sql`).
- **Recuperar la contraseña:** `POST /api/auth/olvide-password` y `POST /api/auth/restablecer-password` (ver sección 2). El enlace dura 1 hora y es de un solo uso; pedir otro anula el anterior. En la BD no se guarda el token del enlace sino su HMAC, así que leyendo la BD no se pueden cambiar contraseñas ajenas. Cada JWT lleva la `version_sesion` del usuario: al cambiar la contraseña sube en 1 y los JWT viejos dejan de valer. Límite: 10 peticiones cada 10 minutos por IP (`LIMITE_RECUPERAR`). Migración `V7__recuperar_password.sql`.
- **Borrar la cuenta y cambiar la contraseña:** `POST /api/cuenta/borrar` y `POST /api/cuenta/cambiar-password` (ver "Mi cuenta" en la sección 2). La fila del usuario no se borra: se le quitan los datos personales y se marca `usuarios.borrada_en`, porque hay votos, jerseys y ganadores que apuntan a ella (migración `V8__borrar_cuenta_y_avisos.sql`). El admin ya no puede cambiar el rol ni desbloquear una cuenta borrada (`409 CUENTA_BORRADA`).
- **Avisos por correo:** al empezar y cerrarse la votación, al publicar los ganadores y cuando un voto deja de contar (ver "Qué correos manda el backend"). Las fechas de envío se guardan en `configuracion` (V8).
- **Solo mayores de 18:** la casilla del registro pasa de `mayorDe14` a `mayorDe18` ("Debes tener 18 años o más para participar"). En la BD, `mayor_14_confirmado` se sustituye por `mayor_18_confirmado`; las cuentas que ya existían quedan en `false`, porque confirmaron 14 y no 18 (migración V5).
