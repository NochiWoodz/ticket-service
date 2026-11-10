package cl.duoc.tickets.exception;

/** El checksum del archivo descargado no coincide con el registrado al generarlo. */
public class IntegridadArchivoException extends RuntimeException {
    public IntegridadArchivoException(String mensaje) { super(mensaje); }
}
