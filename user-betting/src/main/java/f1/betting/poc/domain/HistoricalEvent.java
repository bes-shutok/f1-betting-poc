package f1.betting.poc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "historical_events")
@Getter
@Setter
public class HistoricalEvent {
	@Id
	@Column(name = "event_id", nullable = false)
	private Long eventId;

	@Column(name = "event_name")
	private String eventName;

	@Column(name = "country")
	private String country;

	private Integer year;

	@Enumerated(EnumType.STRING)
	private EventStatus status = EventStatus.OPEN;

	@Column(name = "created_at", updatable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();

	@Column(name = "updated_at")
	private OffsetDateTime updatedAt = OffsetDateTime.now();

}
