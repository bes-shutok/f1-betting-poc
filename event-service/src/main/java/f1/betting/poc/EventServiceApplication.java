package f1.betting.poc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class EventServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run( EventServiceApplication.class, args);
	}

}
