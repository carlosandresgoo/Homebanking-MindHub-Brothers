package com.mindhub.homebanking.service.export;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.mindhub.homebanking.domain.Transaction;
import com.mindhub.homebanking.domain.TransactionType;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * An account statement ("resumen de cuenta") for a period as a PDF: holder and account data, opening
 * balance, every movement oldest first with its running balance, totals and closing balance.
 */
public final class StatementDocument {

    /** Everything the statement shows; {@code movements} are oldest first. */
    public record Statement(String holder, String accountNumber, String cbu, String alias, LocalDate from,
                            LocalDate to, BigDecimal opening, List<Transaction> movements, LocalDateTime issuedAt) {

        public BigDecimal credits() {
            return total(TransactionType.CREDIT);
        }

        public BigDecimal debits() {
            return total(TransactionType.DEBIT);
        }

        public BigDecimal closing() {
            return movements.isEmpty() ? opening : movements.getLast().getBalanceAfter();
        }

        private BigDecimal total(TransactionType type) {
            return movements.stream().filter(t -> t.getType() == type).map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private static final Locale ES_AR = Locale.of("es", "AR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", ES_AR);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ES_AR);
    private static final Color BRAND = new Color(11, 87, 208);
    private static final Color HEADER_FILL = new Color(220, 227, 241);
    private static final Color LINE = new Color(210, 214, 222);
    private static final Color MUTED = new Color(91, 98, 112);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.NORMAL, BRAND);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 12, Font.NORMAL, MUTED);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.NORMAL, MUTED);
    private static final Font TEXT = FontFactory.getFont(FontFactory.HELVETICA, 9.5f);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f);
    private static final Font BIG = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font FOOTER = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.NORMAL, MUTED);

    private StatementDocument() {
    }

    public static byte[] write(Statement s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 44, 52);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Footer("Resumen de la cuenta " + s.accountNumber() + " · emitido el "
                    + s.issuedAt().format(DATE_TIME)));
            document.addTitle("Resumen de cuenta " + s.accountNumber());
            document.addCreator("MindHub Brothers");
            document.open();

            document.add(new Paragraph("MindHub Brothers", TITLE));
            Paragraph subtitle = new Paragraph("Resumen de cuenta · del " + s.from().format(DATE) + " al "
                    + s.to().format(DATE), SUBTITLE);
            subtitle.setSpacingAfter(14);
            document.add(subtitle);

            document.add(accountData(s));
            document.add(totals(s));
            document.add(movements(s));

            Paragraph note = new Paragraph("Los importes están expresados en pesos argentinos. Este resumen es "
                    + "informativo; ante cualquier diferencia, comunicate con soporte.", FOOTER);
            note.setSpacingBefore(12);
            document.add(note);
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not write the statement", e);
        }
        return out.toByteArray();
    }

    private static PdfPTable accountData(Statement s) {
        PdfPTable table = new PdfPTable(new float[]{1, 1, 1});
        table.setWidthPercentage(100);
        table.addCell(field("Titular", s.holder()));
        table.addCell(field("Cuenta", s.accountNumber()));
        table.addCell(field("Alias", s.alias()));
        PdfPCell cbu = field("CBU", s.cbu().substring(0, 8) + " " + s.cbu().substring(8));
        cbu.setColspan(2);
        table.addCell(cbu);
        table.addCell(field("Período", s.from().format(DATE) + " al " + s.to().format(DATE)));
        table.setSpacingAfter(12);
        return table;
    }

    private static PdfPTable totals(Statement s) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.addCell(amountBox("Saldo inicial", s.opening()));
        table.addCell(amountBox("Créditos", s.credits()));
        table.addCell(amountBox("Débitos", s.debits().negate()));
        table.addCell(amountBox("Saldo final", s.closing()));
        table.setSpacingAfter(14);
        return table;
    }

    private static PdfPTable movements(Statement s) {
        PdfPTable table = new PdfPTable(new float[]{13, 45, 14, 14, 14});
        table.setWidthPercentage(100);
        table.setHeaderRows(1); // repeated on every page
        for (String header : new String[]{"Fecha", "Descripción", "Débito", "Crédito", "Saldo"}) {
            PdfPCell cell = cell(header, BOLD, header.equals("Fecha") || header.equals("Descripción")
                    ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            cell.setBackgroundColor(HEADER_FILL);
            table.addCell(cell);
        }
        if (s.movements().isEmpty()) {
            PdfPCell empty = cell("No hubo movimientos en el período.", TEXT, Element.ALIGN_LEFT);
            empty.setColspan(5);
            table.addCell(empty);
            return table;
        }
        for (Transaction t : s.movements()) {
            boolean credit = t.getType() == TransactionType.CREDIT;
            table.addCell(cell(t.getDate().format(DATE), TEXT, Element.ALIGN_LEFT));
            table.addCell(cell(t.getDescription(), TEXT, Element.ALIGN_LEFT));
            table.addCell(cell(credit ? "" : money(t.getAmount()), TEXT, Element.ALIGN_RIGHT));
            table.addCell(cell(credit ? money(t.getAmount()) : "", TEXT, Element.ALIGN_RIGHT));
            table.addCell(cell(money(t.getBalanceAfter()), TEXT, Element.ALIGN_RIGHT));
        }
        return table;
    }

    private static PdfPCell field(String label, String value) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label + "\n", LABEL));
        phrase.add(new Chunk(value, BOLD));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(8);
        return cell;
    }

    private static PdfPCell amountBox(String label, BigDecimal amount) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label + "\n", LABEL));
        phrase.add(new Chunk(money(amount), BIG));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorderColor(LINE);
        cell.setPadding(8);
        return cell;
    }

    private static PdfPCell cell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setPaddingTop(4);
        cell.setPaddingBottom(5);
        return cell;
    }

    /** es-AR, e.g. "$ 1.234,50" (NumberFormat is not thread-safe: one per call). */
    static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(ES_AR).format(amount);
    }

    /** "…· Página N" at the bottom of every page. */
    private static final class Footer extends PdfPageEventHelper {

        private final String text;

        Footer(String text) {
            this.text = text;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase(text + " · Página " + writer.getPageNumber(), FOOTER),
                    (document.left() + document.right()) / 2, document.bottom() - 24, 0);
        }
    }
}
