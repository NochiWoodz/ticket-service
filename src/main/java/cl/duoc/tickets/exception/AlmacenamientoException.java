package cl.duoc.tickets.exception;

/** Error al leer/escribir en EFS o S3. */
public class AlmacenamientoException extends RuntimeException {
    public AlmacenamientoException(String mensaje, Throwable causa) { super(mensaje, causa); }
    public AlmacenamientoException(String mensaje) { super(mensaje); }
}
