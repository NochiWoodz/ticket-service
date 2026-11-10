package cl.duoc.tickets.service;

import cl.duoc.tickets.config.AppProperties;
import cl.duoc.tickets.exception.AlmacenamientoException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Almacenamiento temporal de tickets en Amazon EFS.
 *
 * En la instancia EC2 el EFS se monta en /mnt/efs (NFS v4.1) y se pasa al contenedor con
 * "docker run -v /mnt/efs:/mnt/efs". Para la aplicacion es un directorio local mas, lo que
 * permite que varias instancias/contenedores compartan los mismos archivos y que estos
 * sobrevivan a la recreacion del contenedor en cada despliegue.
 *
 * Estructura: {EFS_BASE_PATH}/tickets/{evento}/{usuario}/{ticket}.pdf
 */
@Service
public class EfsStorageService {

    private static final Logger log = LoggerFactory.getLogger(EfsStorageService.class);

    private final Path raizTickets;
    private final Path basePath;

    public EfsStorageService(AppProperties props) {
        this.basePath = Paths.get(props.efs().basePath()).toAbsolutePath().normalize();
        this.raizTickets = basePath.resolve(props.efs().ticketsDir()).normalize();
    }

    @PostConstruct
    void inicializar() {
        try {
            Files.createDirectories(raizTickets);
            log.info("EFS listo en {}", raizTickets);
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo inicializar el directorio EFS " + raizTickets, e);
        }
    }

    /** Guarda el PDF en EFS y retorna la ruta absoluta. */
    public Path guardar(String eventoCodigo, String usuarioId, String ticketCodigo, byte[] contenido) {
        Path destino = resolver(eventoCodigo, usuarioId, ticketCodigo);
        try {
            Files.createDirectories(destino.getParent());
            // Escritura atomica: primero a .tmp y luego move, para no dejar PDFs a medias
            Path tmp = destino.resolveSibling(destino.getFileName() + ".tmp");
            Files.write(tmp, contenido);
            Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Ticket guardado en EFS: {} ({} bytes)", destino, contenido.length);
            return destino;
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.write(destino, contenido);
                return destino;
            } catch (IOException ex) {
                throw new AlmacenamientoException("Error escribiendo en EFS: " + destino, ex);
            }
        } catch (IOException e) {
            throw new AlmacenamientoException("Error escribiendo en EFS: " + destino, e);
        }
    }

    public byte[] leer(String rutaAbsoluta) {
        Path p = validarDentroDeRaiz(Paths.get(rutaAbsoluta));
        try {
            return Files.readAllBytes(p);
        } catch (NoSuchFileException e) {
            throw new AlmacenamientoException("El archivo ya no existe en EFS: " + p);
        } catch (IOException e) {
            throw new AlmacenamientoException("Error leyendo de EFS: " + p, e);
        }
    }

    public boolean existe(String rutaAbsoluta) {
        return rutaAbsoluta != null && Files.exists(Paths.get(rutaAbsoluta));
    }

    public void eliminar(String rutaAbsoluta) {
        if (rutaAbsoluta == null) return;
        Path p = validarDentroDeRaiz(Paths.get(rutaAbsoluta));
        try {
            if (Files.deleteIfExists(p)) {
                log.info("Archivo eliminado de EFS: {}", p);
                limpiarDirectoriosVacios(p.getParent());
            }
        } catch (IOException e) {
            throw new AlmacenamientoException("Error eliminando de EFS: " + p, e);
        }
    }

    /** Lista los archivos presentes en EFS (opcionalmente filtrados por evento/usuario). */
    public List<Map<String, Object>> listar(String eventoCodigo, String usuarioId) {
        Path dir = raizTickets.resolve(RutaArchivoUtil.prefijo(eventoCodigo, usuarioId)).normalize();
        validarDentroDeRaiz(dir);
        List<Map<String, Object>> resultado = new ArrayList<>();
        if (!Files.exists(dir)) return resultado;
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(Files::isRegularFile)
             .filter(p -> p.toString().endsWith(".pdf"))
             .sorted()
             .forEach(p -> {
                 Map<String, Object> m = new LinkedHashMap<>();
                 m.put("rutaRelativa", raizTickets.relativize(p).toString().replace('\\', '/'));
                 m.put("rutaAbsoluta", p.toString());
                 try {
                     m.put("tamanoBytes", Files.size(p));
                     m.put("ultimaModificacion", Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis()).toString());
                 } catch (IOException ignored) { }
                 resultado.add(m);
             });
        } catch (IOException e) {
            throw new AlmacenamientoException("Error listando EFS", e);
        }
        return resultado;
    }

    /** Estado del volumen: util para demostrar que el EFS esta montado y es escribible. */
    public Map<String, Object> estado() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("basePath", basePath.toString());
        m.put("directorioTickets", raizTickets.toString());
        m.put("existe", Files.exists(raizTickets));
        m.put("escribible", Files.isWritable(raizTickets));
        try {
            FileStore store = Files.getFileStore(raizTickets);
            m.put("tipoSistemaArchivos", store.type());   // "nfs4" cuando el EFS esta montado
            m.put("dispositivo", store.name());
            m.put("espacioDisponibleBytes", store.getUsableSpace());
        } catch (IOException e) {
            m.put("tipoSistemaArchivos", "desconocido");
        }
        m.put("archivosPdf", listar(null, null).size());
        return m;
    }

    private Path resolver(String eventoCodigo, String usuarioId, String ticketCodigo) {
        Path p = raizTickets.resolve(RutaArchivoUtil.rutaRelativa(eventoCodigo, usuarioId, ticketCodigo)).normalize();
        return validarDentroDeRaiz(p);
    }

    private Path validarDentroDeRaiz(Path p) {
        Path n = p.toAbsolutePath().normalize();
        if (!n.startsWith(raizTickets)) {
            throw new IllegalArgumentException("Ruta fuera del directorio permitido de EFS");
        }
        return n;
    }

    private void limpiarDirectoriosVacios(Path dir) {
        try {
            while (dir != null && !dir.equals(raizTickets) && dir.startsWith(raizTickets)) {
                try (Stream<Path> s = Files.list(dir)) {
                    if (s.findAny().isPresent()) return;
                }
                Files.delete(dir);
                dir = dir.getParent();
            }
        } catch (IOException e) {
            log.debug("No se pudo limpiar directorio {}: {}", dir, e.getMessage());
        }
    }
}
