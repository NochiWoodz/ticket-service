package cl.duoc.tickets.repository;

import cl.duoc.tickets.model.EstadoTicket;
import cl.duoc.tickets.model.Evento;
import cl.duoc.tickets.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    Optional<Ticket> findByCodigo(String codigo);
    List<Ticket> findByEvento(Evento evento);
    List<Ticket> findByS3KeyIsNull();
    long countByEventoAndEstadoIn(Evento evento, Collection<EstadoTicket> estados);
}
