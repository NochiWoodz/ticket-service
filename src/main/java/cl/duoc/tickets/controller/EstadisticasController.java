package cl.duoc.tickets.controller;

import cl.duoc.tickets.dto.EstadisticasEventoResponse;
import cl.duoc.tickets.service.EstadisticasService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 6. Reporte de estadisticas de ventas y reservas por evento. */
@RestController
@RequestMapping("/api/estadisticas")
public class EstadisticasController {

    private final EstadisticasService service;

    public EstadisticasController(EstadisticasService service) {
        this.service = service;
    }

    @GetMapping("/eventos")
    public List<EstadisticasEventoResponse> todos() { return service.todos(); }

    @GetMapping("/eventos/{codigo}")
    public EstadisticasEventoResponse porEvento(@PathVariable String codigo) { return service.porEvento(codigo); }
}
