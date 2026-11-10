package cl.duoc.tickets.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
public class S3Config {

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    /**
     * Cliente S3 con la cadena de credenciales por defecto del SDK:
     * 1) variables AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_SESSION_TOKEN
     * 2) perfil ~/.aws/credentials
     * 3) rol IAM de la instancia EC2 (Instance Profile, ej. LabInstanceProfile)  <- recomendado
     * Asi nunca se escriben credenciales en el codigo ni en la imagen Docker.
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(AppProperties props) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.s3().region()))
                .credentialsProvider(DefaultCredentialsProvider.create());

        String endpoint = props.s3().endpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            // Solo para pruebas locales (LocalStack/MinIO)
            log.warn("Usando endpoint S3 personalizado: {}", endpoint);
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        log.info("Cliente S3 configurado. Bucket={}, region={}", props.s3().bucket(), props.s3().region());
        return builder.build();
    }
}
