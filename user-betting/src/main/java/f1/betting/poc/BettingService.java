package f1.betting.poc;

import f1.betting.poc.domain.*;
import f1.betting.poc.web.BetResponse;
import f1.betting.poc.web.PlaceBetRequest;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BettingService {

	private final UserRepository userRepository;
	private final HistoricalEventRepository historicalEventRepository;
	private final BetRepository betRepository;
	private final EventOutcomeRepository eventOutcomeRepository;
	private final RestTemplate restTemplate;

	@Value("${event-service.base-url:http://localhost:8081}")
	private String eventServiceBaseUrl;

	/**
	 * Place a single bet
	 */
	@Transactional
	public BetResponse placeBet(@NotNull PlaceBetRequest request) {
		// --- 1. Pre-check: event exists & get drivers/odds
		EventDetails eventDetails = fetchEventFromEventService(request.eventId());
		Driver driver = eventDetails.getDrivers().stream()
				.filter(d -> d.getDriverNumber().equals(request.driverId()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Driver not found in event"));

		if (request.amountEur() == null || request.amountEur() <= 0) {
			throw new IllegalArgumentException("Bet must be positive");
		}

		// --- 2. Transactional placement
		// Insert event if not exists
		historicalEventRepository.insertIfNotExists(
				eventDetails.getSessionKey(),
				eventDetails.getSessionName(),
				eventDetails.getCountryName(),
				eventDetails.getDateStart() == null ? null : eventDetails.getDateStart().getYear(),
				"OPEN"
		);

		// Lock event row
		HistoricalEvent event = historicalEventRepository.findById(eventDetails.getSessionKey())
				.orElseThrow(() -> new IllegalStateException("Event should exist now"));
		if (event.getStatus() != EventStatus.OPEN) {
			throw new IllegalStateException("Event is not open for betting");
		}

		// Load user entity to attach to bet
		User user = userRepository.findById(request.userId())
				.orElseThrow(() -> new IllegalArgumentException("User not found"));

		// Debit user
		int updated = userRepository.debitUser(request.userId(), request.amountEur());
		if (updated == 0) throw new IllegalStateException("Insufficient balance");

		// Insert bet
		Bet bet = new Bet();
		bet.setUser(user);
		bet.setEventId(event.getEventId());
		bet.setDriverId(driver.getDriverNumber());
		bet.setDriverName(driver.getFullName());
		bet.setAmountEur(request.amountEur());
		bet.setOdds(driver.getOdds());
		bet.setStatus(BetStatus.PENDING);

		bet = betRepository.save(bet);

		return new BetResponse(
				bet.getId(),
				bet.getEventId(),
				bet.getDriverId(),
				bet.getAmountEur(),
				bet.getOdds(),
				bet.getStatus().name()
		);
	}

	/**
	 * Trigger event settlement
	 */
	@Transactional
	public void settleEvent(Long eventId, Long winningDriverId) {
		// Lock event for settlement
		HistoricalEvent event = historicalEventRepository.findById(eventId)
				.orElseThrow(() -> new IllegalArgumentException("Event not found"));
		if (event.getStatus() != EventStatus.OPEN) {
			throw new IllegalStateException("Event is already locked or settled");
		}
		event.setStatus(EventStatus.LOCKED);

		// Fetch bets
		List<Bet> bets = betRepository.findByEventId(eventId);

		for (Bet bet : bets) {
			if (bet.getDriverId().equals(winningDriverId)) {
				bet.setStatus(BetStatus.WON);
				long payout = bet.getAmountEur() * bet.getOdds();
				User u = bet.getUser();
				u.setBalanceEur(u.getBalanceEur() + payout);
			} else {
				bet.setStatus(BetStatus.LOST);
			}
		}
		betRepository.saveAll(bets);

		// Save event outcome
		EventOutcome outcome = new EventOutcome();
		outcome.setEventId(eventId);
		outcome.setWinningDriverId(winningDriverId);
		eventOutcomeRepository.save(outcome);

		// Set event as settled
		event.setStatus(EventStatus.SETTLED);
		historicalEventRepository.save(event);
	}

	private EventDetails fetchEventFromEventService(@NotNull Long eventId) {
		String url = eventServiceBaseUrl + "/api/events/" + eventId;
		return restTemplate.getForObject(url, EventDetails.class);
	}
}
