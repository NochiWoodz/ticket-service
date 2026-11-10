package cl.duoc.tickets.service;

import cl.duoc.tickets.model.AccionArchivo;
import cl.duoc.tickets.model.HistorialArchivo;
import cl.duoc.tickets.model.Ticket;
import cl.duoc.tickets.repository.HistorialArchivoRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class HistorialService {

    private final HistorialArchivoRepository repo;

    public HistorialService(HistorialArchivoRepository repo) {
        this.repo = repo;
    }

    public void registrar(Ticket t, AccionArchivo accion, String ubicacion, String detalle) {
        repo.save(new HistorialArchivo(t, accion, ubicacion, detalle));
    }

    /**
     * Consulta del historial de archivos con filtros combinables (todos opcionales).
     * Se usa Criteria API (Specification) para construir solo los predicados enviados,
     * evitando SQL dinamico concatenado (prevencion de SQL Injection, OWASP A03).
     */
    public List<HistorialArchivo> buscar(String eventoCodigo, String usuarioId, String ticketCodigo,
                                         AccionArchivo accion, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException("'hasta' no puede ser anterior a 'desde'");
        }
        Specification<HistorialArchivo> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (notBlank(eventoCodigo)) p.add(cb.equal(root.get("eventoCodigo"), eventoCodigo));
            if (notBlank(usuarioId)) p.add(cb.equal(root.get("usuarioId"), usuarioId));
            if (notBlank(ticketCodigo)) p.add(cb.equal(root.get("ticketCodigo"), ticketCodigo));
            if (accion != null) p.add(cb.equal(root.get("accion"), accion));
            if (desde != null) p.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde.atStartOfDay()));
            if (hasta != null) p.add(cb.lessThan(root.get("fecha"), hasta.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return repo.findAll(spec, Sort.by(Sort.Direction.DESC, "fecha").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
