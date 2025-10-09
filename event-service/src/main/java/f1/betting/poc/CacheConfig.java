package f1.betting.poc;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

	@Value("${app.cache.ttl-minutes:60}")
	private long ttlMinutes;

	@Bean
	public Caffeine<Object, Object> caffeineConfig() {
		return Caffeine.newBuilder()
				.expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
				.maximumSize(1000);
	}

	@Bean
	public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
		CaffeineCacheManager manager = new CaffeineCacheManager("driversBySession","events","eventById");
		manager.setCaffeine(caffeine);
		return manager;
	}
}

