package cl.duoc.tickets.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Codigo legible del ticket, usado como nombre de archivo (ej: ticket001). */
    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "evento_id")
    private Evento evento;

    /** Identificador del usuario comprador (segundo nivel de carpeta: usuarioX). */
    @Column(nullable = false, length = 60)
    private String usuarioId;

    @Column(nullable = false, length = 120)
    private String nombreAsistente;

    @Column(length = 120)
    private String emailAsistente;

    @Column(nullable = false, length = 40)
    private String categoria;

    @Column(length = 20)
    private String asiento;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTicket estado;

    /** Ruta absoluta del PDF en el EFS (null si ya fue limpiado). */
    @Column(length = 400)
    private String rutaEfs;

    /** Key del objeto en S3 (evento/usuario/ticket.pdf). Null si aun no se sube. */
    @Column(length = 400)
    private String s3Key;

    /** SHA-256 del PDF, para verificar integridad en la descarga. */
    @Column(length = 64)
    private String checksumSha256;

    private Long tamanoBytes;

    /** Version del archivo: se incrementa cada vez que se modifica y regenera. */
    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaActualizacion;

    private LocalDateTime fechaSubidaS3;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public Evento getEvento() { return evento; }
    public void setEvento(Evento evento) { this.evento = evento; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getNombreAsistente() { return nombreAsistente; }
    public void setNombreAsistente(String nombreAsistente) { this.nombreAsistente = nombreAsistente; }
    public String getEmailAsistente() { return emailAsistente; }
    public void setEmailAsistente(String emailAsistente) { this.emailAsistente = emailAsistente; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getAsiento() { return asiento; }
    public void setAsiento(String asiento) { this.asiento = asiento; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public EstadoTicket getEstado() { return estado; }
    public void setEstado(EstadoTicket estado) { this.estado = estado; }
    public String getRutaEfs() { return rutaEfs; }
    public void setRutaEfs(String rutaEfs) { this.rutaEfs = rutaEfs; }
    public String getS3Key() { return s3Key; }
    public void setS3Key(String s3Key) { this.s3Key = s3Key; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
    public LocalDateTime getFechaSubidaS3() { return fechaSubidaS3; }
    public void setFechaSubidaS3(LocalDateTime fechaSubidaS3) { this.fechaSubidaS3 = fechaSubidaS3; }
}
