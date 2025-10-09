package f1.betting.poc.domain;

import lombok.*;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResult {
	private String sessionKey;
	private boolean finished;
	private Integer winnerDriverNumber; // null when not finished
	private OffsetDateTime providerFetchedAt;
}
