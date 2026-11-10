package cl.duoc.tickets.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Bitacora inmutable de cada operacion sobre los archivos de tickets.
 * Se conserva aunque el ticket se elimine (por eso no hay FK, solo ids/codigos).
 */
@Entity
@Table(name = "historial_archivos")
public class HistorialArchivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ticketId;

    @Column(nullable = false, length = 40)
    private String ticketCodigo;

    @Column(nullable = false, length = 60)
    private String eventoCodigo;

    @Column(nullable = false, length = 60)
    private String usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccionArchivo accion;

    @Column(length = 400)
    private String ubicacion;

    private Integer versionArchivo;

    @Column(length = 64)
    private String checksumSha256;

    @Column(length = 300)
    private String detalle;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public HistorialArchivo() {}

    public HistorialArchivo(Ticket t, AccionArchivo accion, String ubicacion, String detalle) {
        this.ticketId = t.getId();
        this.ticketCodigo = t.getCodigo();
        this.eventoCodigo = t.getEvento().getCodigo();
        this.usuarioId = t.getUsuarioId();
        this.accion = accion;
        this.ubicacion = ubicacion;
        this.versionArchivo = t.getVersion();
        this.checksumSha256 = t.getChecksumSha256();
        this.detalle = detalle;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public String getTicketCodigo() { return ticketCodigo; }
    public String getEventoCodigo() { return eventoCodigo; }
    public String getUsuarioId() { return usuarioId; }
    public AccionArchivo getAccion() { return accion; }
    public String getUbicacion() { return ubicacion; }
    public Integer getVersionArchivo() { return versionArchivo; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getDetalle() { return detalle; }
    public LocalDateTime getFecha() { return fecha; }
}
