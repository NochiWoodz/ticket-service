package cl.duoc.tickets.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<Map<String, Object>> conflicto(ConflictoException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception ex, HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errores = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(),
                        f -> f.getDefaultMessage() == null ? "invalido" : f.getDefaultMessage(),
                        (a, b) -> a, LinkedHashMap::new));
        return respuesta(HttpStatus.BAD_REQUEST, "Datos de entrada invalidos", req, errores);
    }

    @ExceptionHandler(IntegridadArchivoException.class)
    public ResponseEntity<Map<String, Object>> integridad(IntegridadArchivoException ex, HttpServletRequest req) {
        log.error("Fallo de integridad: {}", ex.getMessage());
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req, null);
    }

    @ExceptionHandler(AlmacenamientoException.class)
    public ResponseEntity<Map<String, Object>> almacenamiento(AlmacenamientoException ex, HttpServletRequest req) {
        log.error("Error de almacenamiento", ex);
        return respuesta(HttpStatus.BAD_GATEWAY, ex.getMessage(), req, null);
    }

    @ExceptionHandler(SdkException.class)
    public ResponseEntity<Map<String, Object>> aws(SdkException ex, HttpServletRequest req) {
        log.error("Error AWS SDK", ex);
        return respuesta(HttpStatus.BAD_GATEWAY, "Error al comunicarse con AWS S3: " + ex.getMessage(), req, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, "Cuerpo JSON invalido o con valores no permitidos", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generico(Exception ex, HttpServletRequest req) {
        // Excepciones propias de Spring MVC (404 ruta inexistente, 405, 400 por parametro faltante, etc.)
        if (ex instanceof ErrorResponse er) {
            HttpStatus st = HttpStatus.resolve(er.getStatusCode().value());
            return respuesta(st != null ? st : HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
        }
        log.error("Error no controlado", ex);
        // No se expone el stacktrace al cliente (buena practica de seguridad)
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", req, null);
    }

    private ResponseEntity<Map<String, Object>> respuesta(HttpStatus status, String mensaje,
                                                          HttpServletRequest req, Object detalles) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("mensaje", mensaje);
        body.put("ruta", req.getRequestURI());
        if (detalles != null) body.put("detalles", detalles);
        return ResponseEntity.status(status).body(body);
    }
}
