package cl.duoc.tickets.service;

import cl.duoc.tickets.config.AppProperties;
import cl.duoc.tickets.dto.GenerarTicketRequest;
import cl.duoc.tickets.dto.ModificarTicketRequest;
import cl.duoc.tickets.dto.TicketResponse;
import cl.duoc.tickets.exception.AlmacenamientoException;
import cl.duoc.tickets.exception.ConflictoException;
import cl.duoc.tickets.exception.IntegridadArchivoException;
import cl.duoc.tickets.exception.RecursoNoEncontradoException;
import cl.duoc.tickets.model.*;
import cl.duoc.tickets.repository.TicketRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Orquesta el ciclo de vida del ticket:
 *   generar PDF -> guardar en EFS (temporal) -> subir a S3 (evento/usuario/ticket.pdf)
 *   -> descargar (con verificacion de integridad) -> modificar (regenera y sobrescribe en S3) -> eliminar
 * Cada operacion sobre archivos queda registrada en el historial.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final Set<EstadoTicket> ACTIVOS = EnumSet.of(EstadoTicket.RESERVADO, EstadoTicket.VENDIDO);

    private final TicketRepository ticketRepo;
    private final EventoService eventoService;
    private final EfsStorageService efs;
    private final S3StorageService s3;
    private final TicketPdfGenerator pdfGenerator;
    private final HistorialService historial;
    private final AppProperties props;

    public TicketService(TicketRepository ticketRepo, EventoService eventoService, EfsStorageService efs,
                         S3StorageService s3, TicketPdfGenerator pdfGenerator, HistorialService historial,
                         AppProperties props) {
        this.ticketRepo = ticketRepo;
        this.eventoService = eventoService;
        this.efs = efs;
        this.s3 = s3;
        this.pdfGenerator = pdfGenerator;
        this.historial = historial;
        this.props = props;
    }

    /** Resultado de una descarga: nombre, contenido y checksum verificado. */
    public record ArchivoDescargado(String nombre, String contentType, byte[] contenido, String sha256) {}

    // ------------------------------------------------------------------ GENERAR

    @Transactional
    public TicketResponse generar(GenerarTicketRequest req, Boolean subirS3) {
        Evento evento = eventoService.obtener(req.eventoCodigo());
        EstadoTicket estado = req.estado() == null ? EstadoTicket.RESERVADO : req.estado();
        if (estado == EstadoTicket.CANCELADO) {
            throw new IllegalArgumentException("No se puede generar un ticket en estado CANCELADO");
        }
        validarCapacidad(evento);

        Ticket t = new Ticket();
        t.setCodigo(UUID.randomUUID().toString()); // temporal hasta obtener el id
        t.setEvento(evento);
        t.setUsuarioId(req.usuarioId());
        t.setNombreAsistente(req.nombreAsistente());
        t.setEmailAsistente(req.emailAsistente());
        t.setCategoria(req.categoria());
        t.setAsiento(req.asiento());
        t.setPrecio(req.precio() != null ? req.precio() : evento.getPrecioBase());
        t.setEstado(estado);
        t.setVersion(1);
        t.setFechaCreacion(LocalDateTime.now());
        t = ticketRepo.save(t);
        t.setCodigo(String.format("ticket%03d", t.getId()));

        // 1) Generar PDF y guardarlo en EFS
        byte[] pdf = generarYGuardarEnEfs(t);
        historial.registrar(t, AccionArchivo.GENERADO_EFS, t.getRutaEfs(), "Ticket generado en EFS");

        // 2) Subida automatica a S3 (configurable por propiedad y por query param)
        boolean subir = subirS3 != null ? subirS3 : props.s3().autoUpload();
        if (subir) {
            subirInterno(t, pdf, "Subida automatica tras generar");
        }
        return respuesta(ticketRepo.save(t));
    }

    // ------------------------------------------------------------------ SUBIR A S3

    @Transactional
    public TicketResponse subir(Long id) {
        Ticket t = buscar(id);
        byte[] pdf = obtenerPdfLocal(t);
        subirInterno(t, pdf, t.getS3Key() == null ? "Subida manual" : "Re-subida manual (sobrescribe)");
        return respuesta(ticketRepo.save(t));
    }

    /** Sube todos los tickets que aun no estan en S3. */
    @Transactional
    public List<TicketResponse> subirPendientes() {
        List<TicketResponse> subidos = new ArrayList<>();
        for (Ticket t : ticketRepo.findByS3KeyIsNull()) {
            subirInterno(t, obtenerPdfLocal(t), "Subida masiva de pendientes");
            subidos.add(respuesta(ticketRepo.save(t)));
        }
        return subidos;
    }

    // ------------------------------------------------------------------ DESCARGAR

    @Transactional
    public ArchivoDescargado descargar(Long id) {
        Ticket t = buscar(id);
        byte[] contenido = descargarVerificado(t);
        historial.registrar(t, AccionArchivo.DESCARGADO, "s3://" + s3.getBucket() + "/" + t.getS3Key(),
                "Descarga individual, integridad SHA-256 verificada");
        return new ArchivoDescargado(t.getCodigo() + ".pdf", "application/pdf", contenido, t.getChecksumSha256());
    }

    /** Descarga en un ZIP todos los tickets de un evento y/o usuario desde S3. */
    @Transactional
    public ArchivoDescargado descargarZip(String eventoCodigo, String usuarioId) {
        if (isBlank(eventoCodigo) && isBlank(usuarioId)) {
            throw new IllegalArgumentException("Debe indicar eventoCodigo y/o usuarioId");
        }
        List<Ticket> tickets = filtrar(eventoCodigo, usuarioId, null).stream()
                .filter(t -> t.getS3Key() != null)
                .toList();
        if (tickets.isEmpty()) {
            throw new RecursoNoEncontradoException("No hay tickets subidos a S3 para los filtros indicados");
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(baos)) {
            for (Ticket t : tickets) {
                byte[] contenido = descargarVerificado(t);
                zip.putNextEntry(new ZipEntry(t.getS3Key())); // conserva estructura evento/usuario/ticket.pdf
                zip.write(contenido);
                zip.closeEntry();
                historial.registrar(t, AccionArchivo.DESCARGADO, "s3://" + s3.getBucket() + "/" + t.getS3Key(),
                        "Descarga masiva (zip) evento=" + eventoCodigo + " usuario=" + usuarioId);
            }
            zip.finish();
            byte[] bytes = baos.toByteArray();
            String nombre = "tickets"
                    + (isBlank(eventoCodigo) ? "" : "_" + eventoCodigo)
                    + (isBlank(usuarioId) ? "" : "_" + usuarioId) + ".zip";
            return new ArchivoDescargado(nombre, "application/zip", bytes, ChecksumUtil.sha256Hex(bytes));
        } catch (IOException e) {
            throw new AlmacenamientoException("Error armando el ZIP de tickets", e);
        }
    }

    // ------------------------------------------------------------------ MODIFICAR

    @Transactional
    public TicketResponse modificar(Long id, ModificarTicketRequest req) {
        Ticket t = buscar(id);
        List<String> cambios = new ArrayList<>();

        String keyAnterior = t.getS3Key();
        String rutaEfsAnterior = t.getRutaEfs();

        if (req.estado() != null && req.estado() != t.getEstado()) {
            if (t.getEstado() == EstadoTicket.CANCELADO && ACTIVOS.contains(req.estado())) {
                validarCapacidad(t.getEvento());
            }
            cambios.add("estado: " + t.getEstado() + " -> " + req.estado());
            t.setEstado(req.estado());
        }
        if (req.usuarioId() != null && !req.usuarioId().equals(t.getUsuarioId())) {
            cambios.add("usuarioId: " + t.getUsuarioId() + " -> " + req.usuarioId());
            t.setUsuarioId(req.usuarioId());
        }
        if (req.nombreAsistente() != null && !req.nombreAsistente().equals(t.getNombreAsistente())) {
            cambios.add("nombreAsistente");
            t.setNombreAsistente(req.nombreAsistente());
        }
        if (req.emailAsistente() != null && !req.emailAsistente().equals(t.getEmailAsistente())) {
            cambios.add("emailAsistente");
            t.setEmailAsistente(req.emailAsistente());
        }
        if (req.categoria() != null && !req.categoria().equals(t.getCategoria())) {
            cambios.add("categoria: " + t.getCategoria() + " -> " + req.categoria());
            t.setCategoria(req.categoria());
        }
        if (req.asiento() != null && !req.asiento().equals(t.getAsiento())) {
            cambios.add("asiento: " + t.getAsiento() + " -> " + req.asiento());
            t.setAsiento(req.asiento());
        }
        if (req.precio() != null && req.precio().compareTo(t.getPrecio()) != 0) {
            cambios.add("precio: " + t.getPrecio() + " -> " + req.precio());
            t.setPrecio(req.precio());
        }
        if (cambios.isEmpty()) {
            throw new IllegalArgumentException("No se enviaron cambios para el ticket " + t.getCodigo());
        }

        t.setVersion(t.getVersion() + 1);
        t.setFechaActualizacion(LocalDateTime.now());

        // Regenerar el PDF con los nuevos datos (nueva version) en EFS
        byte[] pdf = generarYGuardarEnEfs(t);
        if (rutaEfsAnterior != null && !rutaEfsAnterior.equals(t.getRutaEfs())) {
            efs.eliminar(rutaEfsAnterior); // el usuario cambio: se mueve de carpeta
        }
        String detalle = "v" + t.getVersion() + " - " + String.join(", ", cambios);
        historial.registrar(t, AccionArchivo.MODIFICADO, t.getRutaEfs(), detalle);

        // Actualizar el objeto en S3 (sobrescribe misma key, o mueve si cambio el usuario)
        if (keyAnterior != null || props.s3().autoUpload()) {
            subirInterno(t, pdf, "Actualizacion tras modificar (" + detalle + ")");
            if (keyAnterior != null && !keyAnterior.equals(t.getS3Key())) {
                s3.eliminar(keyAnterior);
                log.info("Key anterior eliminada de S3 por cambio de ruta: {}", keyAnterior);
            }
        }
        return respuesta(ticketRepo.save(t));
    }

    // ------------------------------------------------------------------ ELIMINAR

    @Transactional
    public void eliminar(Long id) {
        Ticket t = buscar(id);
        List<String> ubicaciones = new ArrayList<>();
        if (t.getS3Key() != null) {
            s3.eliminar(t.getS3Key());
            ubicaciones.add("s3://" + s3.getBucket() + "/" + t.getS3Key());
        }
        if (t.getRutaEfs() != null) {
            efs.eliminar(t.getRutaEfs());
            ubicaciones.add(t.getRutaEfs());
        }
        historial.registrar(t, AccionArchivo.ELIMINADO, String.join(" | ", ubicaciones),
                "Ticket eliminado de S3, EFS y base de datos");
        ticketRepo.delete(t);
    }

    // ------------------------------------------------------------------ CONSULTAS

    @Transactional(readOnly = true)
    public TicketResponse obtener(Long id) {
        return respuesta(buscar(id));
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listar(String eventoCodigo, String usuarioId, EstadoTicket estado) {
        return filtrar(eventoCodigo, usuarioId, estado).stream().map(this::respuesta).toList();
    }

    // ------------------------------------------------------------------ PRIVADOS

    private List<Ticket> filtrar(String eventoCodigo, String usuarioId, EstadoTicket estado) {
        Specification<Ticket> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!isBlank(eventoCodigo)) p.add(cb.equal(root.get("evento").get("codigo"), eventoCodigo));
            if (!isBlank(usuarioId)) p.add(cb.equal(root.get("usuarioId"), usuarioId));
            if (estado != null) p.add(cb.equal(root.get("estado"), estado));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return ticketRepo.findAll(spec, Sort.by("id"));
    }

    private Ticket buscar(Long id) {
        return ticketRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ticket no encontrado: " + id));
    }

    private void validarCapacidad(Evento evento) {
        long ocupados = ticketRepo.countByEventoAndEstadoIn(evento, ACTIVOS);
        if (ocupados >= evento.getCapacidad()) {
            throw new ConflictoException("Evento " + evento.getCodigo() + " sin capacidad disponible ("
                    + ocupados + "/" + evento.getCapacidad() + ")");
        }
    }

    private byte[] generarYGuardarEnEfs(Ticket t) {
        byte[] pdf = pdfGenerator.generar(t);
        Path ruta = efs.guardar(t.getEvento().getCodigo(), t.getUsuarioId(), t.getCodigo(), pdf);
        t.setRutaEfs(ruta.toString());
        t.setChecksumSha256(ChecksumUtil.sha256Hex(pdf));
        t.setTamanoBytes((long) pdf.length);
        return pdf;
    }

    /** Lee el PDF desde EFS; si fue limpiado (modo temporal), lo regenera. */
    private byte[] obtenerPdfLocal(Ticket t) {
        if (efs.existe(t.getRutaEfs())) {
            byte[] pdf = efs.leer(t.getRutaEfs());
            if (ChecksumUtil.sha256Hex(pdf).equals(t.getChecksumSha256())) {
                return pdf;
            }
            log.warn("Checksum distinto en EFS para {}, se regenera el PDF", t.getCodigo());
        }
        byte[] pdf = generarYGuardarEnEfs(t);
        historial.registrar(t, AccionArchivo.GENERADO_EFS, t.getRutaEfs(), "PDF regenerado en EFS");
        return pdf;
    }

    private void subirInterno(Ticket t, byte[] pdf, String detalle) {
        String key = RutaArchivoUtil.rutaRelativa(t.getEvento().getCodigo(), t.getUsuarioId(), t.getCodigo());
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("evento", t.getEvento().getCodigo());
        meta.put("usuario", t.getUsuarioId());
        meta.put("ticket", t.getCodigo());
        meta.put("version", String.valueOf(t.getVersion()));
        meta.put("estado", t.getEstado().name());
        meta.put("fecha-generacion", LocalDateTime.now().toString());
        meta.put("sha256", t.getChecksumSha256());
        s3.subir(key, pdf, meta);
        t.setS3Key(key);
        t.setFechaSubidaS3(LocalDateTime.now());
        historial.registrar(t, AccionArchivo.SUBIDO_S3, "s3://" + s3.getBucket() + "/" + key, detalle);

        if (props.efs().deleteAfterUpload() && t.getRutaEfs() != null) {
            efs.eliminar(t.getRutaEfs());
            t.setRutaEfs(null);
        }
    }

    private byte[] descargarVerificado(Ticket t) {
        if (t.getS3Key() == null) {
            throw new ConflictoException("El ticket " + t.getCodigo()
                    + " aun no se ha subido a S3. Use POST /api/tickets/" + t.getId() + "/subir");
        }
        byte[] contenido = s3.descargar(t.getS3Key());
        String sha = ChecksumUtil.sha256Hex(contenido);
        if (t.getChecksumSha256() != null && !sha.equals(t.getChecksumSha256())) {
            throw new IntegridadArchivoException("Integridad comprometida en " + t.getS3Key()
                    + ": SHA-256 esperado " + t.getChecksumSha256() + " pero se obtuvo " + sha);
        }
        return contenido;
    }

    private TicketResponse respuesta(Ticket t) {
        return TicketResponse.de(t, s3.getBucket(), efs.existe(t.getRutaEfs()));
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
