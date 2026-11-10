package cl.duoc.tickets.service;

import cl.duoc.tickets.config.AppProperties;
import cl.duoc.tickets.exception.AlmacenamientoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operaciones sobre Amazon S3. Los tickets se organizan por evento y usuario:
 *   s3://{bucket}/{evento}/{usuario}/{ticket}.pdf   (ej: evento123/usuarioX/ticket001.pdf)
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3;
    private final String bucket;

    public S3StorageService(S3Client s3, AppProperties props) {
        this.s3 = s3;
        this.bucket = props.s3().bucket();
    }

    public String getBucket() { return bucket; }

    /**
     * Sube (o sobrescribe) un objeto. Se envia el checksum SHA-256 para que S3 valide la
     * integridad del archivo en el servidor y se agregan metadatos (fecha, usuario, evento).
     */
    public String subir(String key, byte[] contenido, Map<String, String> metadatos) {
        try {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/pdf")
                    .contentLength((long) contenido.length)
                    .checksumAlgorithm(ChecksumAlgorithm.SHA256)
                    .metadata(metadatos)
                    .build();
            PutObjectResponse resp = s3.putObject(req, RequestBody.fromBytes(contenido));
            log.info("Subido a S3: s3://{}/{} (ETag {})", bucket, key, resp.eTag());
            return resp.eTag();
        } catch (S3Exception e) {
            throw new AlmacenamientoException("Error subiendo a S3 la key " + key + ": "
                    + mensaje(e), e);
        }
    }

    private static String mensaje(S3Exception e) {
        return e.awsErrorDetails() != null && e.awsErrorDetails().errorMessage() != null
                ? e.awsErrorDetails().errorMessage() : e.getMessage();
    }

    public byte[] descargar(String key) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return bytes.asByteArray();
        } catch (NoSuchKeyException e) {
            throw new AlmacenamientoException("El objeto no existe en S3: " + key);
        } catch (S3Exception e) {
            throw new AlmacenamientoException("Error descargando de S3 la key " + key + ": "
                    + mensaje(e), e);
        }
    }

    public boolean existe(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }

    public void eliminar(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        log.info("Eliminado de S3: s3://{}/{}", bucket, key);
    }

    /** Lista los objetos bajo un prefijo (paginado automaticamente por el SDK). */
    public List<Map<String, Object>> listar(String prefijo) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        ListObjectsV2Request req = ListObjectsV2Request.builder().bucket(bucket).prefix(prefijo).build();
        s3.listObjectsV2Paginator(req).contents().forEach(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", o.key());
            m.put("tamanoBytes", o.size());
            m.put("ultimaModificacion", o.lastModified().toString());
            m.put("eTag", o.eTag());
            resultado.add(m);
        });
        return resultado;
    }
}
