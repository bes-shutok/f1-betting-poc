package f1.betting.poc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String username;

	@Column(name = "balance_eur", nullable = false)
	private long balanceEur;

	@Column(name = "created_at", updatable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();

	@Column(name = "updated_at")
	private OffsetDateTime updatedAt = OffsetDateTime.now();

}

