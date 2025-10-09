package f1.betting.poc.domain;

import lombok.*;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResult {
	private Long sessionKey;
	private boolean finished;
	private Long winnerDriverNumber; // null when not finished
	private OffsetDateTime providerFetchedAt;
}
