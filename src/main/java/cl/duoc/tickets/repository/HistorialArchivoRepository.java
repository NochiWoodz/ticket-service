package cl.duoc.tickets.repository;

import cl.duoc.tickets.model.HistorialArchivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HistorialArchivoRepository
        extends JpaRepository<HistorialArchivo, Long>, JpaSpecificationExecutor<HistorialArchivo> {
}
