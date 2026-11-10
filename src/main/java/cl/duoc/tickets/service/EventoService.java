package cl.duoc.tickets.service;

import cl.duoc.tickets.dto.EventoRequest;
import cl.duoc.tickets.exception.ConflictoException;
import cl.duoc.tickets.exception.RecursoNoEncontradoException;
import cl.duoc.tickets.model.Evento;
import cl.duoc.tickets.repository.EventoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EventoService {

    private final EventoRepository repo;

    public EventoService(EventoRepository repo) {
        this.repo = repo;
    }

    public List<Evento> listar() { return repo.findAll(); }

    public Evento obtener(String codigo) {
        return repo.findByCodigo(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Evento no encontrado: " + codigo));
    }

    @Transactional
    public Evento crear(EventoRequest r) {
        if (repo.existsByCodigo(r.codigo())) {
            throw new ConflictoException("Ya existe un evento con codigo " + r.codigo());
        }
        return repo.save(new Evento(r.codigo(), r.nombre(), r.lugar(), r.fecha(), r.capacidad(), r.precioBase()));
    }
}
