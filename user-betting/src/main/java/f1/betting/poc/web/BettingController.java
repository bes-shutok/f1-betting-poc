package f1.betting.poc.web;

import f1.betting.poc.BettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BettingController {

	private final BettingService bettingService;

	@PostMapping("/bets")
	public ResponseEntity<BetResponse> placeBet(@Valid @RequestBody PlaceBetRequest request) {
		BetResponse response = bettingService.placeBet(request);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/events/{eventId}/settle")
	public ResponseEntity<Void> settleEvent(@PathVariable Long eventId) {
		bettingService.settleEvent(eventId );
		return ResponseEntity.ok().build();
	}
}

