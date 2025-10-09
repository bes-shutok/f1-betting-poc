package f1.betting.poc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bets")
@Getter
@Setter
public class Bet {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

 	@Column(name = "event_id", nullable = false)
	private Long eventId;

	@Column(name = "driver_id", nullable = false)
	private Long driverId;

	@Column(name = "driver_name")
	private String driverName;

	@Column(name = "amount_eur", nullable = false)
	private long amountEur;

	@Column(nullable = false)
	private int odds;

	@Enumerated(EnumType.STRING)
	private BetStatus status = BetStatus.PENDING;

	@Column(name = "created_at", updatable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();

}

