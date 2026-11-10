package cl.duoc.tickets;

import cl.duoc.tickets.service.RutaArchivoUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RutaArchivoUtilTest {

    @Test
    void construyeKeyConEstructuraEventoUsuarioTicket() {
        assertThat(RutaArchivoUtil.rutaRelativa("evento123", "usuarioX", "ticket001"))
                .isEqualTo("evento123/usuarioX/ticket001.pdf");
    }

    @Test
    void construyePrefijos() {
        assertThat(RutaArchivoUtil.prefijo("evento123", null)).isEqualTo("evento123/");
        assertThat(RutaArchivoUtil.prefijo("evento123", "usuarioX")).isEqualTo("evento123/usuarioX/");
        assertThat(RutaArchivoUtil.prefijo(null, null)).isEmpty();
    }

    @Test
    void rechazaSegmentosMaliciosos() {
        assertThatThrownBy(() -> RutaArchivoUtil.rutaRelativa("..", "usuarioX", "t1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RutaArchivoUtil.rutaRelativa("evento123", "a/b", "t1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
