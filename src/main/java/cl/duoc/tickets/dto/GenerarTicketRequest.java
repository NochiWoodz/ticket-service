package cl.duoc.tickets.dto;

import cl.duoc.tickets.model.EstadoTicket;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record GenerarTicketRequest(
        @NotBlank String eventoCodigo,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,60}$", message = "solo letras, numeros, '_' o '-'")
        String usuarioId,
        @NotBlank @Size(max = 120) String nombreAsistente,
        @Email @Size(max = 120) String emailAsistente,
        @NotBlank @Size(max = 40) String categoria,
        @Size(max = 20) String asiento,
        /** Opcional: si no se envia se usa el precio base del evento. */
        @DecimalMin("0.0") BigDecimal precio,
        /** RESERVADO o VENDIDO (por defecto RESERVADO). */
        EstadoTicket estado
) {}
