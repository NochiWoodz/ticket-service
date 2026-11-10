package cl.duoc.tickets.service;

import java.util.regex.Pattern;

/**
 * Construye y valida los segmentos de ruta usados tanto en EFS como en S3.
 * Estructura: {eventoCodigo}/{usuarioId}/{ticketCodigo}.pdf  (ej: evento123/usuarioX/ticket001.pdf)
 *
 * La validacion con lista blanca evita Path Traversal (../) y keys S3 maliciosas
 * (OWASP A01 Broken Access Control / CWE-22).
 */
public final class RutaArchivoUtil {

    private static final Pattern SEGMENTO_VALIDO = Pattern.compile("^[A-Za-z0-9_-]{1,60}$");

    private RutaArchivoUtil() {}

    public static String validarSegmento(String valor, String campo) {
        if (valor == null || !SEGMENTO_VALIDO.matcher(valor).matches()) {
            throw new IllegalArgumentException(
                    "El campo '" + campo + "' solo admite letras, numeros, '_' o '-' (1-60 caracteres)");
        }
        return valor;
    }

    public static String nombreArchivo(String ticketCodigo) {
        return validarSegmento(ticketCodigo, "ticketCodigo") + ".pdf";
    }

    /** Key S3 / ruta relativa en EFS para un ticket. */
    public static String rutaRelativa(String eventoCodigo, String usuarioId, String ticketCodigo) {
        return validarSegmento(eventoCodigo, "eventoCodigo") + "/"
                + validarSegmento(usuarioId, "usuarioId") + "/"
                + nombreArchivo(ticketCodigo);
    }

    /** Prefijo S3 para listar/descargar por evento y opcionalmente usuario. */
    public static String prefijo(String eventoCodigo, String usuarioId) {
        StringBuilder sb = new StringBuilder();
        if (eventoCodigo != null && !eventoCodigo.isBlank()) {
            sb.append(validarSegmento(eventoCodigo, "eventoCodigo")).append('/');
            if (usuarioId != null && !usuarioId.isBlank()) {
                sb.append(validarSegmento(usuarioId, "usuarioId")).append('/');
            }
        }
        return sb.toString();
    }
}
