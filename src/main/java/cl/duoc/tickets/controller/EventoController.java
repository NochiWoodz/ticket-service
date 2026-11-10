package cl.duoc.tickets.controller;

import cl.duoc.tickets.dto.EventoRequest;
import cl.duoc.tickets.model.Evento;
import cl.duoc.tickets.service.EventoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final EventoService service;

    public EventoController(EventoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Evento> listar() { return service.listar(); }

    @GetMapping("/{codigo}")
    public Evento obtener(@PathVariable String codigo) { return service.obtener(codigo); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Evento crear(@Valid @RequestBody EventoRequest req) { return service.crear(req); }
}
