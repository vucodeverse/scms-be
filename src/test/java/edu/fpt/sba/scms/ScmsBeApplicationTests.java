package edu.fpt.sba.scms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Smoke test: Spring context (tất cả bean, filter chain, seeder) khởi động được trên H2. */
@SpringBootTest
@ActiveProfiles("test")
class ScmsBeApplicationTests {

	@Test
	void contextLoads() {
	}

}
