package com.jerseys.backend;

import com.jayway.jsonpath.JsonPath;
import com.jerseys.backend.auth.TokenVerificacionRepository;
import com.jerseys.backend.correo.CorreoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de INTEGRACIÓN: arranca la aplicación entera (Spring + PostgreSQL real + Flyway)
 * y hace peticiones HTTP simuladas, igual que las haría el front.
 *
 * Recorre el concurso de principio a fin: registro → verificación → login → subida
 * → votación → ranking → decisión del jurado → resultados, comprobando también que las reglas rechazan lo que deben.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(BaseDeDatosDePrueba.class)
class FlujoCompletoTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private TokenVerificacionRepository tokens;

    /** El servicio de correo de verdad, pero "espiado": podemos ver con qué datos se le llama (p. ej. el enlace). */
    @MockitoSpyBean
    private CorreoService correo;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    @Test
    void concursoDePrincipioAFin() throws Exception {
        String admin = login("admin@test.local", "admin12345");

        // ----- Registro: las casillas son obligatorias -----
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"sin@casillas.com","nombre":"Sin","password":"12345678",
                         "aceptaPrivacidad":false,"aceptaConsentimiento":true,"mayorDe18":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos.aceptaPrivacidad").exists());

        // Hay que confirmar que se es mayor de 18
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"menor@test.com","nombre":"Menor","password":"12345678",
                         "aceptaPrivacidad":true,"aceptaConsentimiento":true,"mayorDe18":false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos.mayorDe18").value("Debes tener 18 años o más para participar"));

        // Correos temporales (de usar y tirar) → 400
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro("tramposo@mailinator.com", "Tramposo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("EMAIL_TEMPORAL"));

        String ana = registrarVerificarYEntrar("ana@test.com", "Ana");
        String beto = registrarVerificarYEntrar("beto@test.com", "Beto");
        String carla = registrarVerificarYEntrar("carla@test.com", "Carla");

        // El mismo buzón de Gmail con otro alias no puede registrarse dos veces
        registrar("pepe.perez@gmail.com", "Pepe");
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro("pepeperez+otra@gmail.com", "Pepe2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_YA_REGISTRADO"));

        // Sin verificar el email no se puede entrar
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pepe.perez@gmail.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_NO_VERIFICADA"));

        // Sin token, las rutas protegidas responden 401
        mvc.perform(get("/api/jerseys/mios")).andExpect(status().isUnauthorized());

        // Sin jerseys todavía: lista vacía (no 404)
        mvc.perform(get("/api/jerseys/mios").header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maximo").value(1))
                .andExpect(jsonPath("$.activos").value(0))
                .andExpect(jsonPath("$.jerseys").isEmpty());

        // ----- Fase de SUBIDA -----
        Instant ahora = Instant.now();
        cambiarFechas(admin, ahora.minus(1, ChronoUnit.HOURS), ahora.plus(1, ChronoUnit.HOURS),
                ahora.plus(2, ChronoUnit.HOURS), ahora.plus(3, ChronoUnit.HOURS),
                ahora.plus(4, ChronoUnit.HOURS), ahora.plus(5, ChronoUnit.HOURS));
        mvc.perform(get("/api/fase")).andExpect(jsonPath("$.fase").value("SUBIDA"));

        // Ana sube su jersey con las dos fotos (delante y detrás); Carla, solo con la de delante
        MvcResult conDosFotos = mvc.perform(multipart("/api/jerseys").file(fotoPng()).file(fotoPng("fotoTrasera"))
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imagenUrl").isNotEmpty())
                .andExpect(jsonPath("$.imagenTraseraUrl").isNotEmpty())
                .andReturn();
        long jerseyAna = ((Number) JsonPath.read(conDosFotos.getResponse().getContentAsString(), "$.id")).longValue();
        long jerseyCarla = subirJersey(carla);
        mvc.perform(get("/api/jerseys/mios").header("Authorization", "Bearer " + carla))
                .andExpect(jsonPath("$.jerseys[0].imagenTraseraUrl").value(nullValue()));

        // Si la foto de detrás no vale, no se guarda NADA (ni la de delante) y el mensaje dice cuál falla
        long archivosAntes = archivosSubidos();
        var traseraFalsa = new MockMultipartFile("fotoTrasera", "detras.png", "image/png", "no soy una imagen".getBytes());
        mvc.perform(multipart("/api/jerseys").file(fotoPng()).file(traseraFalsa).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FORMATO_NO_VALIDO"))
                .andExpect(jsonPath("$.mensaje").value("Foto de detrás: La foto debe ser JPG o PNG"));
        mvc.perform(get("/api/jerseys/mios").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.activos").value(0));
        assertThat(archivosSubidos()).isEqualTo(archivosAntes);

        // Un solo jersey por persona: el segundo → 409
        long jerseyBetoRetirado = subirJersey(beto);
        mvc.perform(multipart("/api/jerseys").file(fotoPng()).header("Authorization", "Bearer " + beto))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIMITE_DE_JERSEYS"));

        // Los eliminados no cuentan: si el admin se lo retira, Beto puede subir otro
        mvc.perform(post("/api/admin/jerseys/" + jerseyBetoRetirado + "/eliminar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Foto borrosa\"}"))
                .andExpect(status().isOk());
        long jerseyBeto = subirJersey(beto);
        mvc.perform(get("/api/jerseys/mios").header("Authorization", "Bearer " + beto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activos").value(1))
                .andExpect(jsonPath("$.jerseys.length()").value(2))
                .andExpect(jsonPath("$.jerseys[?(@.estado == 'ELIMINADO')].motivoEliminacion").value("Foto borrosa"));

        // Un archivo que no es una imagen → 400
        var falso = new MockMultipartFile("foto", "virus.png", "image/png", "no soy una imagen".getBytes());
        mvc.perform(multipart("/api/jerseys").file(falso).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FORMATO_NO_VALIDO"));

        // Todavía no se puede votar
        mvc.perform(get("/api/jerseys").header("Authorization", "Bearer " + ana))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FASE_INCORRECTA"));

        // ----- Fase de VOTACION -----
        cambiarFechas(admin, ahora.minus(3, ChronoUnit.HOURS), ahora.minus(2, ChronoUnit.HOURS),
                ahora.minus(1, ChronoUnit.HOURS), ahora.plus(1, ChronoUnit.HOURS),
                ahora.plus(2, ChronoUnit.HOURS), ahora.plus(3, ChronoUnit.HOURS));

        MvcResult lista = mvc.perform(get("/api/jerseys").header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andReturn();
        // 3 jerseys en concurso (Ana, Carla y Beto; el retirado no sale). La de Ana con sus dos fotos.
        List<Boolean> propios = JsonPath.read(lista.getResponse().getContentAsString(), "$[*].esPropio");
        assertThat(propios).hasSize(3).containsOnlyOnce(true);
        List<String> traseras = JsonPath.read(lista.getResponse().getContentAsString(), "$[*].imagenTraseraUrl");
        assertThat(traseras).filteredOn(t -> t != null).hasSize(1);

        // Votarse a sí misma → 400
        mvc.perform(post("/api/votos").header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votos(Map.of(jerseyAna, 3, jerseyBeto, 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VOTO_PROPIO"));

        // Más de 3 puntos (el sistema antiguo 6-5-4...) → 400
        mvc.perform(post("/api/votos").header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votos(Map.of(jerseyBeto, 6, jerseyCarla, 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        // Con 2 jerseys ajenos solo se puede repartir 3 y 2: el 1 sobra
        mvc.perform(post("/api/votos").header("Authorization", "Bearer " + beto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votos(Map.of(jerseyAna, 2, jerseyCarla, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PUNTOS_INCORRECTOS"));

        mvc.perform(post("/api/votos").header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votos(Map.of(jerseyBeto, 3, jerseyCarla, 2))))
                .andExpect(status().isCreated());

        // Votar otra vez → 409
        mvc.perform(post("/api/votos").header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votos(Map.of(jerseyBeto, 3, jerseyCarla, 2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("YA_HAS_VOTADO"));

        mvc.perform(post("/api/votos").header("Authorization", "Bearer " + carla)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votos(Map.of(jerseyBeto, 3, jerseyAna, 2))))
                .andExpect(status().isCreated());

        // En votación el admin ya no puede eliminar fotos
        mvc.perform(post("/api/admin/jerseys/" + jerseyBeto + "/eliminar").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"prueba\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FASE_INCORRECTA"));

        // ----- Ranking: solo el admin ve los puntos -----
        mvc.perform(get("/api/ranking").header("Authorization", "Bearer " + ana))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));

        // El admin sí: el jersey de Beto gana el popular con 6 puntos (3 + 3)
        mvc.perform(get("/api/ranking").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].jerseyId").value(jerseyBeto))
                .andExpect(jsonPath("$[0].puntos").value(6))
                .andExpect(jsonPath("$[0].autor").value("Beto"));
        mvc.perform(get("/api/admin/jerseys").param("estado", "NOEXISTE").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));

        // ----- Panel de sospechosos -----
        // En este test todos se registran desde 127.0.0.1 en pocos segundos → ráfaga
        mvc.perform(get("/api/admin/sospechosos").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].numCuentas").value(4))
                .andExpect(jsonPath("$.grupos[0].senales[0]").value("RAFAGA_REGISTROS"))
                .andExpect(jsonPath("$.papeletasIdenticas").isEmpty());
        // Solo para ADMIN
        mvc.perform(get("/api/admin/sospechosos").header("Authorization", "Bearer " + ana))
                .andExpect(status().isForbidden());

        // ----- Jurado -----
        // Un usuario normal no puede entrar en las rutas del jurado
        mvc.perform(get("/api/jurado/categorias").header("Authorization", "Bearer " + ana))
                .andExpect(status().isForbidden());

        // El admin nombra jurado a Carla (que ya ha votado en el popular, como cualquiera).
        // El rol se lee de la BD en cada petición: su token sirve sin volver a entrar.
        mvc.perform(patch("/api/admin/usuarios/" + idDe(carla) + "/rol").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"JURADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("JURADO"));
        // El rol PROFESOR ya no existe
        mvc.perform(patch("/api/admin/usuarios/" + idDe(beto) + "/rol").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"PROFESOR\"}"))
                .andExpect(status().isBadRequest());

        // El jurado tampoco ve el ranking
        mvc.perform(get("/api/ranking").header("Authorization", "Bearer " + carla))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/jurado/categorias").header("Authorization", "Bearer " + carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].categoria").value("DIVERTIDO"))
                .andExpect(jsonPath("$[0].nombre").value("El más divertido"))
                .andExpect(jsonPath("$[0].ganador").value(nullValue()));

        // Mientras dura la votación todavía no puede elegir ganadores
        mvc.perform(elegirGanador(carla, "DIVERTIDO", jerseyAna))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FASE_INCORRECTA"));
        // Y los resultados aún no son públicos
        mvc.perform(get("/api/resultados"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FASE_INCORRECTA"));

        // ----- Fase DECISION_JURADO -----
        cambiarFechas(admin, ahora.minus(4, ChronoUnit.HOURS), ahora.minus(3, ChronoUnit.HOURS),
                ahora.minus(2, ChronoUnit.HOURS), ahora.minus(1, ChronoUnit.HOURS),
                ahora.plus(1, ChronoUnit.HOURS), ahora.plus(2, ChronoUnit.HOURS));
        mvc.perform(get("/api/fase")).andExpect(jsonPath("$.fase").value("DECISION_JURADO"));

        // Ya no se puede votar, y el admin ya no puede anular votos (cambiaría el ganador popular)
        mvc.perform(post("/api/votos").header("Authorization", "Bearer " + beto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votos(Map.of(jerseyAna, 3, jerseyCarla, 2))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FASE_INCORRECTA"));
        MvcResult todosLosVotos = mvc.perform(get("/api/admin/votos").header("Authorization", "Bearer " + admin))
                .andReturn();
        Number unVoto = JsonPath.read(todosLosVotos.getResponse().getContentAsString(), "$[0].id");
        mvc.perform(patch("/api/admin/votos/" + unVoto + "/anular").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FASE_INCORRECTA"));

        // El jurado ve todos los jerseys menos el del ganador popular (Beto)
        MvcResult candidatos = mvc.perform(get("/api/jurado/jerseys").header("Authorization", "Bearer " + carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == " + jerseyCarla + ")].esPropio").value(true))
                .andReturn();
        List<Number> idsCandidatos = JsonPath.read(candidatos.getResponse().getContentAsString(), "$[*].id");
        assertThat(idsCandidatos).extracting(Number::longValue).containsExactlyInAnyOrder(jerseyAna, jerseyCarla);

        // El ganador popular no puede ganar otro premio, aunque se mande a mano
        mvc.perform(elegirGanador(carla, "DIVERTIDO", jerseyBeto))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("YA_TIENE_PREMIO"));
        // Carla no puede elegir su propio jersey
        mvc.perform(elegirGanador(carla, "DIVERTIDO", jerseyCarla))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JERSEY_PROPIO"));
        // El premio popular no lo decide el jurado
        mvc.perform(elegirGanador(carla, "POPULAR", jerseyAna))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_NO_VALIDA"));

        mvc.perform(elegirGanador(carla, "DIVERTIDO", jerseyAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("DIVERTIDO"))
                .andExpect(jsonPath("$.ganador.id").value(jerseyAna));

        // Un jersey, un premio: Ana ya ha ganado «El más divertido»
        mvc.perform(elegirGanador(carla, "ORIGINAL", jerseyAna))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("YA_TIENE_PREMIO"))
                .andExpect(jsonPath("$.mensaje").value("Ese jersey ya ha ganado «El más divertido». "
                        + "Solo hay un premio por jersey"));

        // Cambian de opinión: quitan el de «Divertido» y se lo dan en «Original»
        mvc.perform(delete("/api/jurado/ganadores/DIVERTIDO").header("Authorization", "Bearer " + carla))
                .andExpect(status().isNoContent());
        mvc.perform(elegirGanador(carla, "ORIGINAL", jerseyAna)).andExpect(status().isOk());

        mvc.perform(get("/api/jurado/jerseys").header("Authorization", "Bearer " + carla))
                .andExpect(jsonPath("$[?(@.id == " + jerseyAna + ")].ganadorDe").value("ORIGINAL"))
                .andExpect(jsonPath("$[?(@.id == " + jerseyCarla + ")].ganadorDe").value(contains(nullValue())));
        mvc.perform(get("/api/jurado/categorias").header("Authorization", "Bearer " + carla))
                .andExpect(jsonPath("$[?(@.categoria == 'DIVERTIDO')].ganador").value(contains(nullValue())))
                .andExpect(jsonPath("$[?(@.categoria == 'ORIGINAL')].ganador.id").value((int) jerseyAna));

        // El admin puede revisar los resultados antes de publicarlos; el público todavía no
        mvc.perform(get("/api/admin/resultados").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premios[0].categoria").value("POPULAR"))
                .andExpect(jsonPath("$.premios[0].ganador.autor").value("Beto"));
        mvc.perform(get("/api/resultados")).andExpect(status().isForbidden());

        // ----- Fase RESULTADOS -----
        cambiarFechas(admin, ahora.minus(6, ChronoUnit.HOURS), ahora.minus(5, ChronoUnit.HOURS),
                ahora.minus(4, ChronoUnit.HOURS), ahora.minus(3, ChronoUnit.HOURS),
                ahora.minus(2, ChronoUnit.HOURS), ahora.minus(1, ChronoUnit.HOURS));
        mvc.perform(get("/api/fase")).andExpect(jsonPath("$.fase").value("RESULTADOS"));

        // El jurado ya no puede cambiar nada
        mvc.perform(elegirGanador(carla, "NAVIDENO", jerseyAna))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FASE_INCORRECTA"));

        // Resultados públicos (sin iniciar sesión): quién ha ganado, sin puntos
        mvc.perform(get("/api/resultados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premios.length()").value(5))
                .andExpect(jsonPath("$.premios[0].categoria").value("POPULAR"))
                .andExpect(jsonPath("$.premios[0].ganador.jerseyId").value(jerseyBeto))
                .andExpect(jsonPath("$.premios[0].ganador.autor").value("Beto"))
                .andExpect(jsonPath("$.premios[0].ganador.puntos").doesNotExist())
                .andExpect(jsonPath("$.premios[?(@.categoria == 'ORIGINAL')].ganador.autor").value("Ana"))
                // Categoría que el jurado dejó sin elegir: desierta
                .andExpect(jsonPath("$.premios[?(@.categoria == 'DIVERTIDO')].ganador").value(contains(nullValue())));

        // Fechas desordenadas (fin del jurado antes que fin de la votación) → 400
        mvc.perform(put("/api/admin/configuracion").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoFechas(ahora, ahora.plusSeconds(10),
                                ahora.plusSeconds(20), ahora.plusSeconds(30), ahora.plusSeconds(25),
                                ahora.plusSeconds(40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FECHAS_INCORRECTAS"));
    }

    @Test
    void demasiadosLoginsDesdeLaMismaIpDan429() throws Exception {
        String cuerpo = "{\"email\":\"nadie@test.com\",\"password\":\"probando123\"}";
        // Una IP inventada, para no gastar los intentos de 127.0.0.1 que usan los demás tests
        int limite = 30; // app.limites.login.capacidad en application-test.properties
        for (int i = 0; i < limite; i++) {
            mvc.perform(post("/api/auth/login").with(desdeIp("203.0.113.7"))
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(desdeIp("203.0.113.7"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("DEMASIADAS_PETICIONES"));

        // Otra IP sigue pudiendo intentarlo
        mvc.perform(post("/api/auth/login").with(desdeIp("203.0.113.8"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dosSubidasALaVezNoSuperanElMaximo() throws Exception {
        String admin = login("admin@test.local", "admin12345");
        Instant ahora = Instant.now();
        cambiarFechas(admin, ahora.minus(1, ChronoUnit.HOURS), ahora.plus(1, ChronoUnit.HOURS),
                ahora.plus(2, ChronoUnit.HOURS), ahora.plus(3, ChronoUnit.HOURS),
                ahora.plus(4, ChronoUnit.HOURS), ahora.plus(5, ChronoUnit.HOURS));

        // Usuario propio de este test, registrado desde otra IP para no mezclarse con los demás tests
        mvc.perform(post("/api/auth/registro").with(desdeIp("198.51.100.9")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro("dani@test.com", "Dani")))
                .andExpect(status().isCreated());
        String tokenEmail = tokens.findFirstByUsuarioEmailOrderByIdDesc("dani@test.com").orElseThrow().getToken();
        mvc.perform(get("/api/auth/verificar").param("token", tokenEmail)).andExpect(status().isFound());
        String dani = login("dani@test.com", "password123");

        // Todavía no tiene jersey y manda DOS a la vez (doble clic, dos pestañas...)
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        Callable<Integer> subida = () -> {
            salida.await(); // los dos hilos arrancan a la vez
            return mvc.perform(multipart("/api/jerseys").file(fotoPng()).header("Authorization", "Bearer " + dani))
                    .andReturn().getResponse().getStatus();
        };
        Future<Integer> a = hilos.submit(subida);
        Future<Integer> b = hilos.submit(subida);
        salida.countDown();
        List<Integer> estados = List.of(a.get(60, TimeUnit.SECONDS), b.get(60, TimeUnit.SECONDS));
        hilos.shutdown();

        // Exactamente una entra (201) y la otra se rechaza (409)
        assertThat(estados).containsExactlyInAnyOrder(201, 409);
        mvc.perform(get("/api/jerseys/mios").header("Authorization", "Bearer " + dani))
                .andExpect(jsonPath("$.activos").value(1));

        // Limpieza: retiramos el jersey de Dani para que no cuente en el concurso de los otros tests
        MvcResult mios = mvc.perform(get("/api/jerseys/mios").header("Authorization", "Bearer " + dani)).andReturn();
        List<Number> ids = JsonPath.read(mios.getResponse().getContentAsString(), "$.jerseys[*].id");
        assertThat(ids).hasSize(1);
        for (Number id : ids) {
            mvc.perform(post("/api/admin/jerseys/" + id + "/eliminar").header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"limpieza del test\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void heOlvidadoMiContrasena() throws Exception {
        // Usuaria propia de este test, desde otra IP para no mezclarse con los demás tests
        String ip = "192.0.2.50";
        mvc.perform(post("/api/auth/registro").with(desdeIp(ip)).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro("eva@test.com", "Eva")))
                .andExpect(status().isCreated());
        String tokenEmail = tokens.findFirstByUsuarioEmailOrderByIdDesc("eva@test.com").orElseThrow().getToken();
        mvc.perform(get("/api/auth/verificar").param("token", tokenEmail)).andExpect(status().isFound());
        String sesionVieja = login("eva@test.com", "password123");

        // Un email que no existe recibe la MISMA respuesta (no revelamos quién está registrado)
        String respuestaNoExiste = mvc.perform(post("/api/auth/olvide-password").with(desdeIp(ip))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nadie@test.com\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        verify(correo, never()).enviarRecuperacionPassword(eq("nadie@test.com"), any(), any());

        // Eva lo pide dos veces: solo vale el último enlace
        for (int i = 0; i < 2; i++) {
            String respuesta = mvc.perform(post("/api/auth/olvide-password").with(desdeIp(ip))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"Eva@Test.com\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(respuesta).isEqualTo(respuestaNoExiste);
        }
        ArgumentCaptor<String> enlaces = ArgumentCaptor.forClass(String.class);
        verify(correo, times(2)).enviarRecuperacionPassword(eq("eva@test.com"), eq("Eva"), enlaces.capture());
        String primero = tokenDelEnlace(enlaces.getAllValues().get(0));
        String ultimo = tokenDelEnlace(enlaces.getAllValues().get(1));
        assertThat(enlaces.getValue()).startsWith("http://localhost:8080/restablecer-password.html?token=");

        // En la BD no está el token, sino su HMAC: leyendo la BD no se puede usar
        String guardado = tokens.findFirstByUsuarioEmailOrderByIdDesc("eva@test.com").orElseThrow().getToken();
        assertThat(guardado).isNotEqualTo(ultimo).hasSize(64);

        // El primer enlace ya no vale
        mvc.perform(restablecer(primero, "nueva-clave-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENLACE_NO_VALIDO"));
        // Contraseña demasiado corta → 400, y el enlace sigue sirviendo
        mvc.perform(restablecer(ultimo, "corta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.password").exists());

        mvc.perform(restablecer(ultimo, "nueva-clave-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña cambiada. Ya puedes iniciar sesión con la nueva."));
        // Un solo uso
        mvc.perform(restablecer(ultimo, "otra-clave-2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENLACE_NO_VALIDO"));

        // La contraseña vieja ya no entra; la nueva sí
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"eva@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
        String sesionNueva = login("eva@test.com", "nueva-clave-1");

        // Las sesiones que estaban abiertas se han cerrado (por si era alguien con la contraseña vieja)
        mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + sesionVieja))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + sesionNueva))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("eva@test.com"));

        // Quien nunca verificó su email y pide cambiar la contraseña queda verificado:
        // el enlace le ha llegado a su correo, así que el correo es suyo
        mvc.perform(post("/api/auth/registro").with(desdeIp(ip)).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro("fede@test.com", "Fede")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/olvide-password").with(desdeIp(ip))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"fede@test.com\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<String> enlaceFede = ArgumentCaptor.forClass(String.class);
        verify(correo).enviarRecuperacionPassword(eq("fede@test.com"), any(), enlaceFede.capture());
        mvc.perform(restablecer(tokenDelEnlace(enlaceFede.getValue()), "clave-de-fede"))
                .andExpect(status().isOk());
        login("fede@test.com", "clave-de-fede");
    }

    @Test
    void swaggerPublicaElContratoDeLaApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/votos']").exists());
    }

    // ===================== Ayudantes =====================

    private static MockHttpServletRequestBuilder restablecer(String token, String password) {
        return post("/api/auth/restablecer-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password));
    }

    /** El token que va al final del enlace del correo (…?token=XXXX). */
    private static String tokenDelEnlace(String enlace) {
        return enlace.substring(enlace.indexOf("token=") + "token=".length());
    }

    /** Simula que la petición llega desde otra IP. */
    private static RequestPostProcessor desdeIp(String ip) {
        return peticion -> {
            peticion.setRemoteAddr(ip);
            return peticion;
        };
    }

    private String cuerpoRegistro(String email, String nombre) {
        return """
                {"email":"%s","nombre":"%s","password":"password123",
                 "aceptaPrivacidad":true,"aceptaConsentimiento":true,"mayorDe18":true}""".formatted(email, nombre);
    }

    /** El id del usuario dueño de un token. */
    private long idDe(String token) throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token)).andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }

    /** PUT /api/jurado/ganadores/{categoria} con {"jerseyId": ...} */
    private static MockHttpServletRequestBuilder elegirGanador(String token, String categoria, long jerseyId) {
        return put("/api/jurado/ganadores/" + categoria).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"jerseyId\":" + jerseyId + "}");
    }

    private void registrar(String email, String nombre) throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro(email, nombre)))
                .andExpect(status().isCreated());
    }

    private String registrarVerificarYEntrar(String email, String nombre) throws Exception {
        registrar(email, nombre);
        // En un caso real el token llega por correo; aquí lo leemos de la base de datos
        String token = tokens.findFirstByUsuarioEmailOrderByIdDesc(email).orElseThrow().getToken();
        mvc.perform(get("/api/auth/verificar").param("token", token))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:8080/verificacion.html?resultado=ok"));
        return login(email, "password123");
    }

    private String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    private void cambiarFechas(String admin, Instant inicioSubida, Instant finSubida, Instant inicioVotacion,
                               Instant finVotacion, Instant finJurado, Instant publicacionResultados) throws Exception {
        mvc.perform(put("/api/admin/configuracion").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoFechas(inicioSubida, finSubida,
                                inicioVotacion, finVotacion, finJurado, publicacionResultados)))
                .andExpect(status().isOk());
    }

    private static String cuerpoFechas(Instant inicioSubida, Instant finSubida, Instant inicioVotacion,
                                       Instant finVotacion, Instant finJurado, Instant publicacionResultados) {
        return """
                {"inicioSubida":"%s","finSubida":"%s","inicioVotacion":"%s","finVotacion":"%s",
                 "finJurado":"%s","publicacionResultados":"%s"}"""
                .formatted(inicioSubida, finSubida, inicioVotacion, finVotacion, finJurado, publicacionResultados);
    }

    private long subirJersey(String token) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/jerseys").file(fotoPng()).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String votos(Map<Long, Integer> puntosPorJersey) {
        StringBuilder sb = new StringBuilder("{\"votos\":[");
        puntosPorJersey.forEach((jersey, puntos) ->
                sb.append("{\"jerseyId\":").append(jersey).append(",\"puntos\":").append(puntos).append("},"));
        sb.setLength(sb.length() - 1);
        return sb.append("]}").toString();
    }

    /** Foto de delante (campo "foto"). */
    private static MockMultipartFile fotoPng() throws IOException {
        return fotoPng("foto");
    }

    /** Genera en memoria una imagen PNG de 400x300 con transparencia, en el campo del formulario indicado. */
    private static MockMultipartFile fotoPng(String campo) throws IOException {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(50, 50, 300, 200);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(img, "png", salida);
        return new MockMultipartFile(campo, "jersey.png", "image/png", salida.toByteArray());
    }

    /** Cuántas fotos hay guardadas en la carpeta de los tests (app.imagenes.directorio-local). */
    private static long archivosSubidos() throws IOException {
        Path carpeta = Path.of("target", "imagenes-test");
        if (!Files.exists(carpeta)) {
            return 0;
        }
        try (var archivos = Files.list(carpeta)) {
            return archivos.count();
        }
    }
}
