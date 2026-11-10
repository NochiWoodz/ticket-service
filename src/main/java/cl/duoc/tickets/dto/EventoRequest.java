package cl.duoc.tickets.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventoRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,60}$", message = "solo letras, numeros, '_' o '-'")
        String codigo,
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 150) String lugar,
        @NotNull @Future LocalDateTime fecha,
        @NotNull @Min(1) Integer capacidad,
        @NotNull @DecimalMin("0.0") BigDecimal precioBase
) {}
