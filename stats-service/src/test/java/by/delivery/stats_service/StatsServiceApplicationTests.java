package by.delivery.stats_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "spring.grpc.server.port=0")
@Import(TestcontainersConfiguration.class)
class StatsServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
