package cl.duoc.tickets.dto;

import cl.duoc.tickets.model.EstadoTicket;
import cl.duoc.tickets.model.Ticket;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketResponse(
        Long id,
        String codigo,
        String eventoCodigo,
        String eventoNombre,
        String usuarioId,
        String nombreAsistente,
        String emailAsistente,
        String categoria,
        String asiento,
        BigDecimal precio,
        EstadoTicket estado,
        Integer version,
        String rutaEfs,
        boolean enEfs,
        String s3Bucket,
        String s3Key,
        String checksumSha256,
        Long tamanoBytes,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion,
        LocalDateTime fechaSubidaS3
) {
    public static TicketResponse de(Ticket t, String bucket, boolean enEfs) {
        return new TicketResponse(t.getId(), t.getCodigo(), t.getEvento().getCodigo(), t.getEvento().getNombre(),
                t.getUsuarioId(), t.getNombreAsistente(), t.getEmailAsistente(), t.getCategoria(), t.getAsiento(),
                t.getPrecio(), t.getEstado(), t.getVersion(), t.getRutaEfs(), enEfs,
                t.getS3Key() != null ? bucket : null, t.getS3Key(), t.getChecksumSha256(), t.getTamanoBytes(),
                t.getFechaCreacion(), t.getFechaActualizacion(), t.getFechaSubidaS3());
    }
}
