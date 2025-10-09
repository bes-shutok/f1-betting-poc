package f1.betting.poc.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceBetRequest(
		@NotNull Long userId,
		@NotNull Long eventId,
		@NotNull Long driverId,
		@Min(1) Long amountEur
) {}
