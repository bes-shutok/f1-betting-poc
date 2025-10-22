package f1.betting.poc;

import f1.betting.poc.domain.Bet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BetRepository extends JpaRepository<Bet, Long> {

	List<Bet> findByEventId(Long eventId);

	Page<Bet> findByEventId(Long eventId, Pageable pageable);

	List<Bet> findByUserId(Long userId);
}