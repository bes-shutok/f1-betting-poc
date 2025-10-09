package f1.betting.poc.web;

public record BetResponse(
		Long betId,
		Long eventId,
		Long driverId,
		Long amountEur,
		Integer odds,
		String status
) {}
