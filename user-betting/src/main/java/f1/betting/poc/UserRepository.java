package f1.betting.poc;

import f1.betting.poc.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, Long> {

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
 	@Query("UPDATE User u SET u.balanceEur = u.balanceEur - :amount WHERE u.id = :userId AND u.balanceEur >= :amount")
	int debitUser(Long userId, Long amount);
}
