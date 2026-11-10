package cl.duoc.tickets.repository;

import cl.duoc.tickets.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventoRepository extends JpaRepository<Evento, Long> {
    Optional<Evento> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);
}
