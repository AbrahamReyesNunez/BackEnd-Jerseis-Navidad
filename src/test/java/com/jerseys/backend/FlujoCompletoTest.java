package com.jerseys.backend;

import com.jayway.jsonpath.JsonPath;
import com.jerseys.backend.auth.TokenVerificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
 * → votación → ranking, comprobando también que las reglas rechazan lo que deben.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(BaseDeDatosDePrueba.class)
class FlujoCompletoTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private TokenVerificacionRepository tokens;

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
                         "aceptaPrivacidad":false,"aceptaConsentimiento":true,"mayorDe14":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos.aceptaPrivacidad").exists());

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
        mvc.perform(get("/api/jerseys/mio")).andExpect(status().isUnauthorized());

        // ----- Fase de SUBIDA -----
        Instant ahora = Instant.now();
        cambiarFechas(admin, ahora.minus(1, ChronoUnit.HOURS), ahora.plus(1, ChronoUnit.HOURS),
                ahora.plus(2, ChronoUnit.HOURS), ahora.plus(3, ChronoUnit.HOURS));
        mvc.perform(get("/api/fase")).andExpect(jsonPath("$.fase").value("SUBIDA"));

        long jerseyAna = subirJersey(ana);
        long jerseyBeto = subirJersey(beto);
        long jerseyCarla = subirJersey(carla);

        // Un segundo jersey → 409
        mvc.perform(multipart("/api/jerseys").file(fotoPng()).header("Authorization", "Bearer " + ana))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("YA_HAS_SUBIDO"));

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
                ahora.minus(1, ChronoUnit.HOURS), ahora.plus(1, ChronoUnit.HOURS));

        MvcResult lista = mvc.perform(get("/api/jerseys").header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andReturn();
        List<Boolean> propios = JsonPath.read(lista.getResponse().getContentAsString(), "$[*].esPropio");
        assertThat(propios).hasSize(3).containsOnlyOnce(true);

        // Votarse a sí misma → 400
        mvc.perform(post("/api/votos").header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votos(Map.of(jerseyAna, 6, jerseyBeto, 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VOTO_PROPIO"));

        // Con 2 jerseys ajenos se reparten solo 6 y 5
        mvc.perform(post("/api/votos").header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votos(Map.of(jerseyBeto, 6, jerseyCarla, 5))))
                .andExpect(status().isCreated());

        // Votar otra vez → 409
        mvc.perform(post("/api/votos").header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votos(Map.of(jerseyBeto, 6, jerseyCarla, 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("YA_HAS_VOTADO"));

        mvc.perform(post("/api/votos").header("Authorization", "Bearer " + carla)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(votos(Map.of(jerseyBeto, 6, jerseyAna, 5))))
                .andExpect(status().isCreated());

        // En votación el admin ya no puede eliminar fotos
        mvc.perform(post("/api/admin/jerseys/" + jerseyBeto + "/eliminar").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"prueba\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FASE_INCORRECTA"));

        // ----- Ranking -----
        // Un usuario normal no lo puede ver
        mvc.perform(get("/api/ranking").header("Authorization", "Bearer " + ana))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));

        // El admin sí: Beto gana con 12 puntos (6 + 6)
        mvc.perform(get("/api/ranking").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jerseyId").value(jerseyBeto))
                .andExpect(jsonPath("$[0].puntos").value(12))
                .andExpect(jsonPath("$[0].autor").value("Beto"));
    }

    @Test
    void swaggerPublicaElContratoDeLaApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/votos']").exists());
    }

    // ===================== Ayudantes =====================

    private String cuerpoRegistro(String email, String nombre) {
        return """
                {"email":"%s","nombre":"%s","password":"password123",
                 "aceptaPrivacidad":true,"aceptaConsentimiento":true,"mayorDe14":true}""".formatted(email, nombre);
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
                               Instant finVotacion) throws Exception {
        mvc.perform(put("/api/admin/configuracion").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"inicioSubida":"%s","finSubida":"%s","inicioVotacion":"%s","finVotacion":"%s"}"""
                                .formatted(inicioSubida, finSubida, inicioVotacion, finVotacion)))
                .andExpect(status().isOk());
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

    /** Genera en memoria una imagen PNG de 400x300 con transparencia. */
    private static MockMultipartFile fotoPng() throws IOException {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(50, 50, 300, 200);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(img, "png", salida);
        return new MockMultipartFile("foto", "jersey.png", "image/png", salida.toByteArray());
    }
}
