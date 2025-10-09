package f1.betting.poc;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	// The container is shared across tests so using try with resources is not possible
	// The corresponding warning can be ignored
	static final PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>("postgres:16-alpine")
					.withDatabaseName("betting")
					.withUsername("betting")
					.withPassword("betting");

	static {
		postgres.start(); // start container immediately
	}

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		TestPropertyValues.of(
				"spring.datasource.url=" + postgres.getJdbcUrl(),
				"spring.datasource.username=" + postgres.getUsername(),
				"spring.datasource.password=" + postgres.getPassword(),
				"spring.jpa.hibernate.ddl-auto=none",
				"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
				"spring.flyway.enabled=true",
				"spring.flyway.locations=classpath:/migration"
		).applyTo(context.getEnvironment());
	}
}
