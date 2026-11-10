package cl.duoc.tickets.model;

/** Estado comercial del ticket: reserva (sin pagar), venta (pagado) o cancelado. */
public enum EstadoTicket {
    RESERVADO,
    VENDIDO,
    CANCELADO
}
