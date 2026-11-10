package cl.duoc.tickets.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propiedades externalizadas (12-factor): todo se configura por variables de entorno. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Efs efs, S3 s3, Seed seed) {

    public record Efs(String basePath, String ticketsDir, boolean deleteAfterUpload) {}

    public record S3(String bucket, String region, String endpoint, boolean autoUpload) {}

    public record Seed(boolean enabled) {}
}
