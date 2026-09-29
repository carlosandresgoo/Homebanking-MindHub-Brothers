package com.mindhub.homebanking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Boots the full context: also proves Flyway migrations match the JPA mappings (ddl-auto=validate). */
@SpringBootTest
@ActiveProfiles("test")
class HomebankingApplicationTests {

	@Test
	void contextLoads() {
	}

}
