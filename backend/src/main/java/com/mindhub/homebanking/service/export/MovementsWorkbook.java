package com.mindhub.homebanking.service.export;

import com.mindhub.homebanking.domain.Transaction;
import com.mindhub.homebanking.domain.TransactionType;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Function;

/**
 * Movements as an Excel workbook (.xlsx): real dates and numbers (not text), so they can be summed and
 * filtered. Cells are typed values, never formulas, so descriptions cannot inject anything.
 */
public final class MovementsWorkbook {

    private static final String[] HEADERS = {"Fecha", "Descripción", "Categoría", "Tipo", "Importe", "Saldo"};
    private static final String MONEY = "#,##0.00;[Red]-#,##0.00";

    private MovementsWorkbook() {
    }

    /** @param categoryLabel Spanish name of each category, as shown in the app */
    public static byte[] write(String accountNumber, List<Transaction> rows,
                               Function<Transaction, String> categoryLabel) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook workbook = new Workbook(out, "MindHub Brothers", "1.0");
            Worksheet sheet = workbook.newWorksheet("Movimientos " + accountNumber);
            for (int c = 0; c < HEADERS.length; c++) {
                sheet.value(0, c, HEADERS[c]);
            }
            sheet.range(0, 0, 0, HEADERS.length - 1).style().bold().fillColor("DCE3F1").set();
            sheet.freezePane(0, 1);

            int r = 1;
            for (Transaction t : rows) {
                boolean credit = t.getType() == TransactionType.CREDIT;
                sheet.value(r, 0, t.getDate());
                sheet.style(r, 0).format("dd/mm/yyyy hh:mm").set();
                sheet.value(r, 1, t.getDescription());
                sheet.value(r, 2, categoryLabel.apply(t));
                sheet.value(r, 3, credit ? "Ingreso" : "Egreso");
                sheet.value(r, 4, credit ? t.getAmount() : t.getAmount().negate());
                sheet.style(r, 4).format(MONEY).set();
                sheet.value(r, 5, t.getBalanceAfter());
                sheet.style(r, 5).format(MONEY).set();
                r++;
            }
            sheet.width(0, 17);
            sheet.width(1, 55);
            sheet.width(2, 24);
            sheet.width(3, 10);
            sheet.width(4, 16);
            sheet.width(5, 16);
            workbook.finish();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the workbook", e);
        }
        return out.toByteArray();
    }
}
