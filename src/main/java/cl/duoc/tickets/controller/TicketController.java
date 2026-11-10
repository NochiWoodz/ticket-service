package cl.duoc.tickets.controller;

import cl.duoc.tickets.dto.GenerarTicketRequest;
import cl.duoc.tickets.dto.ModificarTicketRequest;
import cl.duoc.tickets.dto.TicketResponse;
import cl.duoc.tickets.model.EstadoTicket;
import cl.duoc.tickets.service.TicketService;
import cl.duoc.tickets.service.TicketService.ArchivoDescargado;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    /** 1. Generar ticket: crea el PDF en EFS y (por defecto) lo sube automaticamente a S3. */
    @PostMapping
    public ResponseEntity<TicketResponse> generar(@Valid @RequestBody GenerarTicketRequest req,
                                                  @RequestParam(required = false) Boolean subirS3) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generar(req, subirS3));
    }

    /** 2a. Subir (o re-subir) un ticket a S3 desde EFS. */
    @PostMapping("/{id}/subir")
    public TicketResponse subir(@PathVariable Long id) {
        return service.subir(id);
    }

    /** 2b. Subir a S3 todos los tickets pendientes. */
    @PostMapping("/subir-pendientes")
    public Map<String, Object> subirPendientes() {
        List<TicketResponse> subidos = service.subirPendientes();
        return Map.of("cantidad", subidos.size(), "tickets", subidos);
    }

    /** 3a. Descargar un ticket especifico desde S3 (PDF). */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        return archivo(service.descargar(id), MediaType.APPLICATION_PDF);
    }

    /** 3b. Descargar todos los tickets de un usuario y/o evento desde S3 (ZIP). */
    @GetMapping("/descargar")
    public ResponseEntity<byte[]> descargarPorFiltro(@RequestParam(required = false) String eventoCodigo,
                                                     @RequestParam(required = false) String usuarioId) {
        return archivo(service.descargarZip(eventoCodigo, usuarioId), MediaType.parseMediaType("application/zip"));
    }

    /** 4. Modificar detalles del ticket: regenera el PDF y actualiza el objeto en S3. */
    @PutMapping("/{id}")
    public TicketResponse modificar(@PathVariable Long id, @Valid @RequestBody ModificarTicketRequest req) {
        return service.modificar(id, req);
    }

    /** 5. Eliminar ticket: borra de S3, EFS y BD (queda registro en el historial). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public TicketResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping
    public List<TicketResponse> listar(@RequestParam(required = false) String eventoCodigo,
                                       @RequestParam(required = false) String usuarioId,
                                       @RequestParam(required = false) EstadoTicket estado) {
        return service.listar(eventoCodigo, usuarioId, estado);
    }

    private ResponseEntity<byte[]> archivo(ArchivoDescargado a, MediaType tipo) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(a.nombre()).build().toString())
                .header("X-Checksum-SHA256", a.sha256())
                .contentLength(a.contenido().length)
                .body(a.contenido());
    }
}
