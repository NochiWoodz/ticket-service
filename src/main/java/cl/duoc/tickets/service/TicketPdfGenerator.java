package cl.duoc.tickets.service;

import cl.duoc.tickets.model.Ticket;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

/** Genera el ticket personalizado en PDF (tamano A6 apaisado). */
@Component
public class TicketPdfGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final Color AZUL = new Color(0, 51, 102);
    private static final Color AMARILLO = new Color(255, 184, 28);

    public byte[] generar(Ticket t) {
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        PDRectangle tamano = new PDRectangle(PDRectangle.A5.getHeight(), PDRectangle.A5.getWidth() / 1.6f);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(tamano);
            doc.addPage(page);

            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("Ticket " + t.getCodigo());
            info.setAuthor("Sistema de Gestion de Reservas de Eventos");
            info.setCreationDate(Calendar.getInstance());

            float w = tamano.getWidth();
            float h = tamano.getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Banda superior
                cs.setNonStrokingColor(AZUL);
                cs.addRect(0, h - 60, w, 60);
                cs.fill();
                cs.setNonStrokingColor(AMARILLO);
                cs.addRect(0, h - 64, w, 4);
                cs.fill();

                texto(cs, bold, 18, Color.WHITE, 24, h - 38, limpiar(t.getEvento().getNombre()));
                texto(cs, normal, 10, Color.WHITE, 24, h - 54,
                        limpiar(t.getEvento().getLugar()) + "  |  " + t.getEvento().getFecha().format(FMT));

                float y = h - 95;
                y = fila(cs, bold, normal, 24, y, "Asistente:", limpiar(t.getNombreAsistente()));
                y = fila(cs, bold, normal, 24, y, "Usuario:", t.getUsuarioId());
                y = fila(cs, bold, normal, 24, y, "Categoria:", limpiar(t.getCategoria())
                        + (t.getAsiento() != null ? "   Asiento: " + limpiar(t.getAsiento()) : ""));
                y = fila(cs, bold, normal, 24, y, "Precio:", "$ " + t.getPrecio().toPlainString());
                fila(cs, bold, normal, 24, y, "Estado:", t.getEstado().name());

                // Talon derecho con codigo
                cs.setStrokingColor(AZUL);
                cs.setLineDashPattern(new float[]{4, 3}, 0);
                cs.moveTo(w - 150, h - 70);
                cs.lineTo(w - 150, 14);
                cs.stroke();
                cs.setLineDashPattern(new float[]{}, 0);

                texto(cs, normal, 9, Color.DARK_GRAY, w - 135, h - 90, "CODIGO TICKET");
                texto(cs, bold, 16, AZUL, w - 135, h - 110, t.getCodigo().toUpperCase());
                texto(cs, normal, 9, Color.DARK_GRAY, w - 135, h - 135, "EVENTO");
                texto(cs, bold, 11, AZUL, w - 135, h - 150, t.getEvento().getCodigo());
                texto(cs, normal, 9, Color.DARK_GRAY, w - 135, h - 175, "VERSION");
                texto(cs, bold, 11, AZUL, w - 135, h - 190, "v" + t.getVersion());

                texto(cs, normal, 7, Color.GRAY, 24, 18,
                        "Generado: " + LocalDateTime.now().format(FMT) + "  -  Documento personal e intransferible");
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF del ticket", e);
        }
    }

    private float fila(PDPageContentStream cs, PDType1Font bold, PDType1Font normal, float x, float y,
                       String etiqueta, String valor) throws IOException {
        texto(cs, bold, 11, AZUL, x, y, etiqueta);
        texto(cs, normal, 11, Color.BLACK, x + 75, y, valor);
        return y - 20;
    }

    private void texto(PDPageContentStream cs, PDType1Font font, float size, Color color,
                       float x, float y, String txt) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(txt == null ? "" : txt);
        cs.endText();
    }

    /** Las fuentes Standard14 usan WinAnsi: se eliminan tildes y caracteres no soportados. */
    static String limpiar(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.replaceAll("[^\\x20-\\x7E]", "");
    }
}
