package f1.betting.poc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
