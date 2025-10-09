package f1.betting.poc;

import org.springframework.boot.SpringApplication;

public class TestApplication {

	public static void main(String[] args) {
		SpringApplication.from( EventServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
