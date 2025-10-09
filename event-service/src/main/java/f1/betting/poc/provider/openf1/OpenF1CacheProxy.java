package f1.betting.poc.provider.openf1;

import f1.betting.poc.provider.openf1.dto.DriverRawDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenF1CacheProxy {

	private final RestTemplate restTemplate;

	@Value("${openf1.base-url:https://api.openf1.org/v1}")
	private String baseUrl;

	@Cacheable("driversBySession")
	public List<DriverRawDto> getDriversForSession(String sessionKey) {
		String url = baseUrl + "/drivers?session_key=" + sessionKey;
		log.info("Calling drivers API: {}", url);
		DriverRawDto[] response = restTemplate.getForObject(url, DriverRawDto[].class);
		return response != null ? Arrays.asList(response) : Collections.emptyList();
	}
}
