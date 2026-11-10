package cl.duoc.tickets.dto;

import cl.duoc.tickets.model.EstadoTicket;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Todos los campos son opcionales: solo se actualiza lo que se envia (semantica PATCH). */
public record ModificarTicketRequest(
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,60}$", message = "solo letras, numeros, '_' o '-'")
        String usuarioId,
        @Size(max = 120) String nombreAsistente,
        @Email @Size(max = 120) String emailAsistente,
        @Size(max = 40) String categoria,
        @Size(max = 20) String asiento,
        @DecimalMin("0.0") BigDecimal precio,
        EstadoTicket estado
) {}
