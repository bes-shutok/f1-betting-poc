package f1.betting.poc.provider;

import f1.betting.poc.domain.EventDetails;
import f1.betting.poc.domain.EventResult;

import java.util.List;
import java.util.Optional;

public interface ProviderAdapter {
	List<EventDetails>  getEvents(String sessionType, String country, Integer year);
	Optional<EventResult> getWinner(Long sessionKey);
}