package cl.duoc.tickets.config;

import cl.duoc.tickets.model.Evento;
import cl.duoc.tickets.repository.EventoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Precarga eventos de ejemplo (solo si no existen) para poder probar de inmediato con Postman. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final EventoRepository repo;
    private final AppProperties props;

    public DataSeeder(EventoRepository repo, AppProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (!props.seed().enabled()) return;
        crear("evento123", "Festival Rock Patagonia", "Estadio Regional, Coyhaique",
                LocalDateTime.now().plusMonths(2).withHour(20).withMinute(0).withSecond(0).withNano(0), 500, "25000");
        crear("evento456", "Conferencia Cloud Native Chile", "Centro de Eventos Duoc UC, Santiago",
                LocalDateTime.now().plusMonths(1).withHour(9).withMinute(0).withSecond(0).withNano(0), 200, "15000");
        crear("evento789", "Stand-up Comedy Night", "Teatro Municipal, Puerto Aysen",
                LocalDateTime.now().plusWeeks(3).withHour(21).withMinute(30).withSecond(0).withNano(0), 3, "8000");
    }

    private void crear(String codigo, String nombre, String lugar, LocalDateTime fecha, int capacidad, String precio) {
        if (repo.existsByCodigo(codigo)) return;
        repo.save(new Evento(codigo, nombre, lugar, fecha, capacidad, new BigDecimal(precio)));
        log.info("Evento de ejemplo creado: {}", codigo);
    }
}
