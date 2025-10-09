package f1.betting.poc;

import f1.betting.poc.domain.EventDetails;
import f1.betting.poc.domain.EventResult;
import f1.betting.poc.provider.openf1.OpenF1ProviderAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EventService {

	private final OpenF1ProviderAdapter adapter;

	/**
	 * Returns all events, applying filters optionally.
	 * Pagination is manual for simplicity.
	 */
	public List<EventDetails> getEvents(String sessionType, String country, Integer year, int page, int size) {
		List<EventDetails> all = adapter.getEvents(sessionType, country, year);
		return paginate(all, page, size);
	}

	private List<EventDetails> paginate(List<EventDetails> list, int page, int size) {
		if (list.isEmpty()) return list;
		int from = page * size;
		if (from >= list.size()) return Collections.emptyList();
		int to = Math.min(from + size, list.size());
		return list.subList(from, to);
	}

	public Optional<EventResult> getWinner(String sessionKey) {
		return adapter.getWinner(sessionKey);
	}
}
