package cl.duoc.tickets.dto;

import java.math.BigDecimal;
import java.util.Map;

public record EstadisticasEventoResponse(
        String eventoCodigo,
        String eventoNombre,
        int capacidad,
        long totalTickets,
        long vendidos,
        long reservados,
        long cancelados,
        long disponibles,
        double porcentajeOcupacion,
        BigDecimal recaudacionVentas,
        BigDecimal montoReservado,
        Map<String, Long> ticketsPorCategoria,
        long usuariosDistintos,
        long archivosEnS3
) {}
