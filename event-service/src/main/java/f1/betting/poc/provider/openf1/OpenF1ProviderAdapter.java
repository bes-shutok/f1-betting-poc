package f1.betting.poc.provider.openf1;

import f1.betting.poc.domain.Driver;
import f1.betting.poc.domain.EventDetails;
import f1.betting.poc.domain.EventResult;
import f1.betting.poc.provider.ProviderAdapter;
import f1.betting.poc.provider.openf1.dto.*;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenF1ProviderAdapter implements ProviderAdapter {

	private final OpenF1Mapper mapper;
	private final RestTemplate restTemplate;
	private final RateLimiterRegistry rateLimiterRegistry;
	private final OpenF1CacheProxy cacheProxy;

	@Value("${openf1.base-url:https://api.openf1.org/v1}")
	private String baseUrl;

	@Override
	@Cacheable(value = "eventById", key = "#eventKey")
	public EventDetails getEvent( Long eventKey ) {
		String url = baseUrl + "/sessions";
		StringBuilder sb = new StringBuilder(url).append("?");
		sb.append("session_key=").append(eventKey);

		String fullUrl = sb.toString();
		log.info("Calling event API: {}", fullUrl);
		SessionRawDto[] sessions = restTemplate.getForObject(fullUrl, SessionRawDto[].class);
		if (sessions == null || sessions.length == 0) return null;
		SessionRawDto session = sessions[0];

		RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("openf1");
		EventDetails ed = mapper.toEventDetails(session);

		// Wrap driver fetch in a rate-limited supplier
		rateLimiter.acquirePermission();
		Supplier<List<DriverRawDto>> supplier = RateLimiter.decorateSupplier(rateLimiter,
				() -> cacheProxy.getDriversForSession(session.getSessionKey()));

		List<DriverRawDto> driverDtos = supplier.get(); // will respect rate limit
		List<Driver> drivers = mapper.toDriverList(driverDtos)
				.stream()
				.peek(d -> d.setOdds(ThreadLocalRandom.current().nextInt(2, 5)))
				.collect(Collectors.toList());

		ed.setDrivers(drivers);
		return ed;
	}

	/**
	 * Fetch sessions and enrich with driver data.
	 */
	@Override
	@Cacheable(value = "events", key = "#sessionType + '-' + #country + '-' + #year")
	public List<EventDetails> getEvents(String sessionType, String country, Integer year) {
		String url = baseUrl + "/sessions";
		StringBuilder sb = new StringBuilder(url).append("?");

		if (sessionType != null) sb.append("session_type=").append(sessionType).append("&");
		if (country != null) sb.append("country_name=").append(country).append("&");
		if (year != null) sb.append("year=").append(year).append("&");

		String fullUrl = sb.toString();
		log.info("Calling event API: {}", fullUrl);
		SessionRawDto[] sessions = restTemplate.getForObject(fullUrl, SessionRawDto[].class);
		if (sessions == null) return Collections.emptyList();

		RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("openf1");

		List<EventDetails> result = new ArrayList<>();
		for (SessionRawDto s : sessions) {
			EventDetails ed = mapper.toEventDetails(s);

			// Wrap driver fetch in a rate-limited supplier
			rateLimiter.acquirePermission();
			Supplier<List<DriverRawDto>> supplier = RateLimiter.decorateSupplier(rateLimiter,
					() -> cacheProxy.getDriversForSession(s.getSessionKey()));

			List<DriverRawDto> driverDtos = supplier.get(); // will respect rate limit
			List<Driver> drivers = mapper.toDriverList(driverDtos)
					.stream()
					.peek(d -> d.setOdds(ThreadLocalRandom.current().nextInt(2, 5)))
					.collect(Collectors.toList());

			ed.setDrivers(drivers);
			result.add(ed);
		}
		return result;
	}

	/**
	 * Get winner for a session based on position = 1
	 */
	@Override
 public Optional<EventResult> getWinner(Long sessionKey) {
		String url = baseUrl + "/position?session_key=" + sessionKey;
		ResultRawDto[] results = restTemplate.getForObject(url, ResultRawDto[].class);
		if (results == null || results.length == 0) {
			return Optional.empty();
		}

		return Arrays.stream(results)
				.filter(r -> r.getPosition() != null && r.getPosition() == 1)
				.findFirst()
				.map(r -> EventResult.builder()
						.sessionKey(sessionKey)
						.winnerDriverNumber(r.getDriverNumber() == null ? null : r.getDriverNumber())
						.finished(true)
						.providerFetchedAt(OffsetDateTime.now())
						.build());
	}
}
