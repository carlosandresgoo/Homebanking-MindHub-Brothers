package com.mindhub.homebanking.controller;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.mindhub.homebanking.domain.TransactionCategory;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Excel export and PDF statements: content, ownership and validation. */
class ExportTest extends IntegrationTest {

    @Autowired
    private Clock clock;

    private String exportUrl() {
        return "/api/accounts/" + ids.accountId() + "/transactions/export";
    }

    private String statementUrl() {
        return "/api/accounts/" + ids.accountId() + "/statement";
    }

    private void transferToOther(String token, String amount) throws Exception {
        mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceAccountNumber", "VIN001", "targetAccountNumber", "VIN999",
                                "amount", amount, "description", "=HYPERLINK(\"http://evil\")"))))
                .andExpect(status().isCreated());
    }

    /**
     * Every part of the .xlsx (a zip), concatenated. Read through the central directory, like Excel does
     * (the entries use data descriptors, which ZipInputStream cannot stream).
     */
    private static String xlsxText(byte[] xlsx) throws IOException {
        Path file = Files.createTempFile("export", ".xlsx");
        try {
            Files.write(file, xlsx);
            StringBuilder text = new StringBuilder();
            try (ZipFile zip = new ZipFile(file.toFile())) {
                for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                    text.append("\n--").append(entry.getName()).append("--\n")
                            .append(new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            // Non-ASCII text is written as character references (Dep&#xf3;sito): decode them to compare.
            return java.util.regex.Pattern.compile("&#x([0-9a-fA-F]+);").matcher(text)
                    .replaceAll(ref -> Character.toString(Integer.parseInt(ref.group(1), 16)));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static String pdfText(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(new PdfTextExtractor(reader).getTextFromPage(page));
        }
        return text.toString();
    }

    @Test
    void excelHasTypedCellsAndNoFormulas() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        transferToOther(melba, "1234.50");

        MvcResult result = mvc.perform(get(exportUrl()).param("format", "xlsx")
                        .header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains(".xlsx");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");

        String xml = xlsxText(result.getResponse().getContentAsByteArray());
        assertThat(xml).contains("xl/worksheets/sheet1.xml")
                .contains("Depósito inicial")
                .contains("-1234.5") // a number, signed, not "$ 1.234,50" text
                .contains("HYPERLINK") // the description is there...
                .doesNotContain("<f>"); // ...but never as a formula
    }

    @Test
    void csvStaysTheDefaultAndUnknownFormatsAreRejected() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get(exportUrl()).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).startsWith("text/csv"));
        mvc.perform(get(exportUrl()).param("format", "pdf").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statementShowsAccountDataBalancesAndMovements() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        transferToOther(melba, "1234.50");

        MvcResult result = mvc.perform(get(statementUrl()).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("resumen-VIN001-");
        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        String text = pdfText(pdf);
        assertThat(text).contains("Resumen de cuenta")
                .contains("Melba Morel")
                .contains("vin001.test")
                .contains("99900018 ") // CBU split in its two blocks
                .contains("Depósito inicial")
                .contains("Transferencia a VIN999")
                .contains("5.000,00")  // credits of the period
                .contains("1.234,50")  // debits
                .contains("3.765,50"); // closing balance
    }

    @Test
    void theOpeningBalanceIsTheOneBeforeThePeriod() throws Exception {
        LocalDate today = LocalDate.now(clock);
        testData.movement("VIN001", true, "100", TransactionCategory.DEPOSIT, "Aporte de ayer",
                today.minusDays(1).atTime(12, 0));
        String melba = accessToken(TestData.CLIENT_EMAIL);

        String text = pdfText(mvc.perform(get(statementUrl()).param("from", today.toString())
                        .param("to", today.toString()).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());

        assertThat(text).contains("Saldo inicial").contains("5.100,00").doesNotContain("Aporte de ayer");
    }

    @Test
    void statementValidationAndAccess() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        LocalDate today = LocalDate.now(clock);
        mvc.perform(get(statementUrl()).param("from", today.toString()).param("to", today.minusDays(1).toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(melba))).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(statementUrl()).param("from", today.minusDays(400).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(statementUrl()).param("from", "ayer").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isBadRequest());

        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        mvc.perform(get(statementUrl()).header(HttpHeaders.AUTHORIZATION, bearer(other))).andExpect(status().isNotFound());
        mvc.perform(get(exportUrl()).param("format", "xlsx").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get(statementUrl()).header(HttpHeaders.AUTHORIZATION, bearer(accessToken(TestData.ADMIN_EMAIL))))
                .andExpect(status().isOk());
        mvc.perform(get(statementUrl())).andExpect(status().isUnauthorized());
    }

    @Test
    void aFutureEndIsCappedAtToday() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        LocalDate today = LocalDate.now(clock);
        MvcResult result = mvc.perform(get(statementUrl()).param("from", today.toString())
                        .param("to", today.plusDays(30).toString()).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains(today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + ".pdf");
    }
}
