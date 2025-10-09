package f1.betting.poc;

import f1.betting.poc.domain.EventDetails;
import f1.betting.poc.domain.EventResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

	private final EventService service;

	@GetMapping
	public ResponseEntity<Map<String, Object>> getEvents(
			@RequestParam(required = false) String sessionType,
			@RequestParam(required = false) String country,
			@RequestParam(required = false) Integer year,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "2") int size
	) {
		List<EventDetails> events = service.getEvents(sessionType, country, year, page, size);
		Map<String, Object> response = new HashMap<>();
		response.put("page", page);
		response.put("size", size);
		response.put("total", events.size());
		response.put("items", events);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{sessionKey}/winner")
	public ResponseEntity<EventResult> getWinner(@PathVariable String sessionKey) {
		return service.getWinner(sessionKey)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}
}
