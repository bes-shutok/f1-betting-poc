package f1.betting.poc;

import f1.betting.poc.domain.HistoricalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface HistoricalEventRepository extends JpaRepository<HistoricalEvent, Long> {

	@Modifying
	@Transactional
	@Query(
			value = "INSERT INTO historical_events(event_id, event_name, country, year, status) " +
					"VALUES (:eventId, :eventName, :country, :year, :status) " +
					"ON CONFLICT (event_id) DO NOTHING",
			nativeQuery = true
	)
	void insertIfNotExists(Long eventId, String eventName, String country, Integer year, String status);
}
