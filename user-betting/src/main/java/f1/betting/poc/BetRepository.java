package f1.betting.poc;

import f1.betting.poc.domain.Bet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BetRepository extends JpaRepository<Bet, Long> {

	List<Bet> findByEventId(Long eventId);

	List<Bet> findByUserId(Long userId);
}
