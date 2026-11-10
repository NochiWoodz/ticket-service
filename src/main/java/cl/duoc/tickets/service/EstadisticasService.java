package cl.duoc.tickets.service;

import cl.duoc.tickets.dto.EstadisticasEventoResponse;
import cl.duoc.tickets.model.EstadoTicket;
import cl.duoc.tickets.model.Evento;
import cl.duoc.tickets.model.Ticket;
import cl.duoc.tickets.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Reporte de ventas (VENDIDO) y reservas (RESERVADO) por evento. */
@Service
public class EstadisticasService {

    private final TicketRepository ticketRepo;
    private final EventoService eventoService;

    public EstadisticasService(TicketRepository ticketRepo, EventoService eventoService) {
        this.ticketRepo = ticketRepo;
        this.eventoService = eventoService;
    }

    @Transactional(readOnly = true)
    public EstadisticasEventoResponse porEvento(String codigo) {
        return calcular(eventoService.obtener(codigo));
    }

    @Transactional(readOnly = true)
    public List<EstadisticasEventoResponse> todos() {
        return eventoService.listar().stream().map(this::calcular).toList();
    }

    private EstadisticasEventoResponse calcular(Evento e) {
        List<Ticket> tickets = ticketRepo.findByEvento(e);

        long vendidos = contar(tickets, EstadoTicket.VENDIDO);
        long reservados = contar(tickets, EstadoTicket.RESERVADO);
        long cancelados = contar(tickets, EstadoTicket.CANCELADO);
        long ocupados = vendidos + reservados;
        long disponibles = Math.max(0, e.getCapacidad() - ocupados);
        double ocupacion = e.getCapacidad() == 0 ? 0
                : BigDecimal.valueOf(ocupados * 100.0 / e.getCapacidad()).setScale(2, RoundingMode.HALF_UP).doubleValue();

        BigDecimal recaudacion = sumar(tickets, EstadoTicket.VENDIDO);
        BigDecimal reservado = sumar(tickets, EstadoTicket.RESERVADO);

        Map<String, Long> porCategoria = tickets.stream()
                .filter(t -> t.getEstado() != EstadoTicket.CANCELADO)
                .collect(Collectors.groupingBy(Ticket::getCategoria, TreeMap::new, Collectors.counting()));

        long usuarios = tickets.stream().map(Ticket::getUsuarioId).distinct().count();
        long enS3 = tickets.stream().filter(t -> t.getS3Key() != null).count();

        return new EstadisticasEventoResponse(e.getCodigo(), e.getNombre(), e.getCapacidad(), tickets.size(),
                vendidos, reservados, cancelados, disponibles, ocupacion, recaudacion, reservado,
                porCategoria, usuarios, enS3);
    }

    private long contar(List<Ticket> ts, EstadoTicket estado) {
        return ts.stream().filter(t -> t.getEstado() == estado).count();
    }

    private BigDecimal sumar(List<Ticket> ts, EstadoTicket estado) {
        return ts.stream().filter(t -> t.getEstado() == estado)
                .map(Ticket::getPrecio).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
