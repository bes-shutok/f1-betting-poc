package f1.betting.poc;

import f1.betting.poc.domain.EventOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventOutcomeRepository extends JpaRepository<EventOutcome, Long> {
}
