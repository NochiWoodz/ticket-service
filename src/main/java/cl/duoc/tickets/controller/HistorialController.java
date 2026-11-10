package cl.duoc.tickets.controller;

import cl.duoc.tickets.model.AccionArchivo;
import cl.duoc.tickets.model.HistorialArchivo;
import cl.duoc.tickets.service.HistorialService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consulta del historial de archivos generados, con filtros por evento, usuario, ticket, accion y fechas. */
@RestController
@RequestMapping("/api/historial")
public class HistorialController {

    private final HistorialService service;

    public HistorialController(HistorialService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> buscar(
            @RequestParam(required = false) String eventoCodigo,
            @RequestParam(required = false) String usuarioId,
            @RequestParam(required = false) String ticketCodigo,
            @RequestParam(required = false) AccionArchivo accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        List<HistorialArchivo> registros = service.buscar(eventoCodigo, usuarioId, ticketCodigo, accion, desde, hasta);

        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("eventoCodigo", eventoCodigo);
        filtros.put("usuarioId", usuarioId);
        filtros.put("ticketCodigo", ticketCodigo);
        filtros.put("accion", accion);
        filtros.put("desde", desde);
        filtros.put("hasta", hasta);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("filtros", filtros);
        resp.put("total", registros.size());
        resp.put("registros", registros);
        return resp;
    }
}
