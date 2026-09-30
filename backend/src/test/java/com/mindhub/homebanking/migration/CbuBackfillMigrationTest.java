package com.mindhub.homebanking.migration;

import com.mindhub.homebanking.domain.Cbu;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** V12 gives accounts that existed before it a valid CBU and a unique alias (the SQL backfill). */
class CbuBackfillMigrationTest {

    private static final String URL = "jdbc:h2:mem:cbu-backfill;DB_CLOSE_DELAY=-1";

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration")
                .target(target).load();
    }

    @Test
    void existingAccountsGetAValidCbuAndAnAlias() throws Exception {
        flyway("11").migrate();
        try (Connection db = DriverManager.getConnection(URL, "sa", ""); Statement sql = db.createStatement()) {
            sql.execute("INSERT INTO client (name, last_name, email, password, role) "
                    + "VALUES ('Ana', 'Old', 'ana@old.com', 'x', 'CLIENT')");
            for (String number : new String[]{"VIN001", "VIN-00000042", "VIN-99999999"}) {
                sql.execute("INSERT INTO account (number, creation_date, balance, client_id) VALUES ('" + number
                        + "', CURRENT_TIMESTAMP, 0, (SELECT id FROM client))");
            }

            flyway("latest").migrate();

            Map<Long, String[]> rows = new HashMap<>();
            try (ResultSet rs = sql.executeQuery("SELECT id, cbu, alias FROM account")) {
                while (rs.next()) {
                    rows.put(rs.getLong(1), new String[]{rs.getString(2), rs.getString(3)});
                }
            }
            assertThat(rows).hasSize(3);
            rows.forEach((id, row) -> {
                assertThat(Cbu.isValid(row[0])).as("CBU %s of account %d", row[0], id).isTrue();
                assertThat(row[0]).isEqualTo(Cbu.of(String.format("%013d", id)));
                assertThat(row[1]).isEqualTo("cuenta." + id);
            });
        }
    }
}
