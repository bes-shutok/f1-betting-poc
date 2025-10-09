package f1.betting.poc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "event_outcomes")
@Getter
@Setter
public class EventOutcome {
	@Id
	@Column(name = "event_id")
	private Long eventId;

	@Column(name = "winning_driver_id", nullable = false)
	private Long winningDriverId;

	@Column(name = "settled_at")
	private OffsetDateTime settledAt = OffsetDateTime.now();

}
