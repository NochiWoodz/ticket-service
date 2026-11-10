package cl.duoc.tickets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Prueba de integracion del ciclo completo: generar -> EFS -> S3 -> descargar -> modificar -> historial -> eliminar.
 * S3 se reemplaza por un mock en memoria (no se requieren credenciales AWS para correr "mvn test"),
 * y el EFS por un directorio temporal.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "app.s3.bucket=bucket-test"
})
@AutoConfigureMockMvc
class TicketLifecycleIntegrationTest {

    @TempDir
    static Path efsTemp;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.efs.base-path", () -> efsTemp.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockBean S3Client s3Client;

    /** "Bucket" simulado: key -> bytes */
    private final Map<String, byte[]> bucket = new ConcurrentHashMap<>();

    @BeforeEach
    void simularS3() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(inv -> {
            PutObjectRequest req = inv.getArgument(0);
            RequestBody body = inv.getArgument(1);
            try (InputStream in = body.contentStreamProvider().newStream()) {
                bucket.put(req.key(), in.readAllBytes());
            }
            return PutObjectResponse.builder().eTag("\"etag-test\"").build();
        });
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenAnswer(inv -> {
            GetObjectRequest req = inv.getArgument(0);
            byte[] data = bucket.get(req.key());
            if (data == null) throw NoSuchKeyException.builder().message("no existe").build();
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), data);
        });
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(inv -> {
            DeleteObjectRequest req = inv.getArgument(0);
            bucket.remove(req.key());
            return DeleteObjectResponse.builder().build();
        });
    }

    @Test
    void cicloCompletoDelTicket() throws Exception {
        // 1. Generar -> queda en EFS y se sube automaticamente a S3 con estructura evento/usuario/ticket.pdf
        String body = """
                {"eventoCodigo":"evento123","usuarioId":"usuarioX","nombreAsistente":"Juan Perez",
                 "emailAsistente":"juan@mail.cl","categoria":"VIP","asiento":"A-12","estado":"VENDIDO"}
                """;
        MvcResult res = mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enEfs").value(true))
                .andExpect(jsonPath("$.s3Key").value(org.hamcrest.Matchers.matchesPattern("evento123/usuarioX/ticket\\d{3,}\\.pdf")))
                .andReturn();
        JsonNode t = om.readTree(res.getResponse().getContentAsString());
        long id = t.get("id").asLong();
        String key = t.get("s3Key").asText();
        String sha = t.get("checksumSha256").asText();

        assertThat(Files.exists(Path.of(t.get("rutaEfs").asText()))).isTrue();
        assertThat(bucket).containsKey(key);
        assertThat(new String(bucket.get(key), 0, 4)).isEqualTo("%PDF");

        // 2. Descargar desde S3 con verificacion de integridad
        mvc.perform(get("/api/tickets/{id}/descargar", id))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Checksum-SHA256", sha))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // 3. Modificar: cambia usuario -> se mueve la key en S3 y sube la version
        mvc.perform(put("/api/tickets/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"usuarioY\",\"categoria\":\"Platea\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.s3Key").value(org.hamcrest.Matchers.startsWith("evento123/usuarioY/")));
        assertThat(bucket).doesNotContainKey(key);

        // 4. Descarga ZIP por evento
        mvc.perform(get("/api/tickets/descargar").param("eventoCodigo", "evento123"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"));

        // 5. Historial filtrado
        mvc.perform(get("/api/historial").param("eventoCodigo", "evento123").param("accion", "MODIFICADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 6. Estadisticas
        mvc.perform(get("/api/estadisticas/eventos/evento123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendidos").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 7. Eliminar -> desaparece de S3 y EFS, pero queda en historial
        mvc.perform(delete("/api/tickets/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/tickets/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/historial").param("accion", "ELIMINADO"))
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void rechazaPathTraversalEnUsuario() throws Exception {
        String body = """
                {"eventoCodigo":"evento123","usuarioId":"../../etc","nombreAsistente":"X","categoria":"General"}
                """;
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void respetaCapacidadDelEvento() throws Exception {
        // evento789 tiene capacidad 3 en el seeder
        String body = """
                {"eventoCodigo":"evento789","usuarioId":"usuarioCap","nombreAsistente":"Ana","categoria":"General"}
                """;
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
}
