package f1.betting.poc;

import f1.betting.poc.domain.*;
import f1.betting.poc.web.BetResponse;
import f1.betting.poc.web.PlaceBetRequest;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
class BettingServiceIntegrationTest {

    @Autowired
    BettingService bettingService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    BetRepository betRepository;
    @Autowired
    HistoricalEventRepository historicalEventRepository;
    @Autowired
    EventOutcomeRepository eventOutcomeRepository;

    @MockitoBean
    RestTemplate restTemplate; // mock external calls to event-service

    Faker faker;

    @BeforeEach
    void setup() {
        faker = new Faker();
    }

    @Test
    void placeBet_shouldPersistBetAndDebitUser_balanceAndResponseMatch() {
        // Arrange: pick an existing seeded user
        List<User> users = userRepository.findAll();
        assertThat(users).isNotEmpty();
        User user = users.get(0);
        long starting = user.getBalanceEur();

        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long driverId = (long) faker.number().numberBetween(1, 99);
        long amount = faker.number().numberBetween(1, 100); // small positive

        Driver driver = Driver.builder()
                .driverNumber(driverId)
                .fullName(faker.name().fullName())
                .teamName("Team-" + faker.team().name())
                .odds(faker.number().numberBetween(2, 5))
                .build();
        EventDetails ed = EventDetails.builder()
                .sessionKey(eventId)
                .sessionName("Session-" + faker.lorem().word())
                .countryName(faker.country().name())
                .driver(driver)
                .build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class))
                .willReturn(ed);

        PlaceBetRequest req = new PlaceBetRequest(user.getId(), eventId, driverId, amount);

        // Act
        BetResponse resp = bettingService.placeBet(req);

        // Assert response fields
        assertThat(resp).isNotNull();
        assertThat(resp.eventId()).isEqualTo(eventId);
        assertThat(resp.driverId()).isEqualTo(driverId);
        assertThat(resp.amountEur()).isEqualTo(amount);
        assertThat(resp.odds()).isEqualTo(driver.getOdds());
        assertThat(resp.status()).isEqualTo(BetStatus.PENDING.name());

        // Assert DB state: bet exists and user debited
        Bet stored = betRepository.findById(resp.betId()).orElseThrow();
        assertThat(stored.getUser().getId()).isEqualTo(user.getId());
        assertThat(stored.getEventId()).isEqualTo(eventId);
        assertThat(stored.getDriverId()).isEqualTo(driverId);
        assertThat(stored.getAmountEur()).isEqualTo(amount);
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getBalanceEur()).isEqualTo(starting - amount);

        // historical event row created
        assertThat(historicalEventRepository.findById(eventId)).isPresent();
    }

    @Test
    void settleEvent_shouldUpdateStatusesAndBalances_andPersistOutcome() {
        // Arrange two users (second may come from seed or we create one)
        List<User> users = userRepository.findAll();
        User u1 = users.get(0);
        User u2 = users.size() > 1 ? users.get(1) : userRepository.save(newUser());
        long start1 = u1.getBalanceEur();
        long start2 = u2.getBalanceEur();

        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long winDriver = (long) faker.number().numberBetween(1, 99);
        Long loseDriver = winDriver + 1;

        // Stub remote event for placeBet calls
        Driver winnerDriver = Driver.builder().driverNumber(winDriver).fullName(faker.name().fullName()).teamName("T1").odds(3).build();
        Driver loserDriver = Driver.builder().driverNumber(loseDriver).fullName(faker.name().fullName()).teamName("T2").odds(2).build();
        EventDetails ed = EventDetails.builder()
                .sessionKey(eventId)
                .sessionName("Race-" + faker.lorem().word())
                .countryName(faker.country().name())
                .drivers(List.of(winnerDriver, loserDriver))
                .build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class))
                .willReturn(ed);

        // Place two bets through the service for realism
        long amount1 = 10L;
        long amount2 = 5L;
        BetResponse b1 = bettingService.placeBet(new PlaceBetRequest(u1.getId(), eventId, winDriver, amount1));
        BetResponse b2 = bettingService.placeBet(new PlaceBetRequest(u2.getId(), eventId, loseDriver, amount2));

        // Stub winner endpoint for settlement
        EventResult winner = EventResult.builder().sessionKey(eventId).finished(true).winnerDriverNumber(winDriver).build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId + "/winner", EventResult.class))
                .willReturn(winner);

        // Act: settle
        bettingService.settleEvent(eventId);

        // Assert: bets updated
        Bet bet1 = betRepository.findById(b1.betId()).orElseThrow();
        Bet bet2 = betRepository.findById(b2.betId()).orElseThrow();
        assertThat(bet1.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(bet2.getStatus()).isEqualTo(BetStatus.LOST);

        // Balances updated
        User u1r = userRepository.findById(u1.getId()).orElseThrow();
        User u2r = userRepository.findById(u2.getId()).orElseThrow();
        assertThat(u1r.getBalanceEur()).isEqualTo(start1 - amount1 + amount1 * bet1.getOdds());
        assertThat(u2r.getBalanceEur()).isEqualTo(start2 - amount2);

        // Outcome persisted and event settled
        EventOutcome outcome = eventOutcomeRepository.findById(eventId).orElseThrow();
        assertThat(outcome.getWinningDriverId()).isEqualTo(winDriver);
        assertThat(historicalEventRepository.findById(eventId).orElseThrow().getStatus()).isEqualTo(EventStatus.SETTLED);
    }

    @Test
    void placeBet_onLockedEvent_shouldFail() {
        // Arrange
        User user = userRepository.findAll().get(0);
        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long driverId = (long) faker.number().numberBetween(1, 99);

        // Seed event as LOCKED
        HistoricalEvent he = new HistoricalEvent();
        he.setEventId(eventId);
        he.setEventName("Race-" + faker.lorem().word());
        he.setCountry(faker.country().name());
        he.setStatus(EventStatus.LOCKED);
        historicalEventRepository.save(he);

        // Mock event-service
        Driver driver = Driver.builder().driverNumber(driverId).fullName(faker.name().fullName()).teamName("T").odds(2).build();
        EventDetails ed = EventDetails.builder().sessionKey(eventId).sessionName("Locked").countryName("X").driver(driver).build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class)).willReturn(ed);

        PlaceBetRequest req = new PlaceBetRequest(user.getId(), eventId, driverId, 5L);

        // Act + Assert
        assertThatThrownBy(() -> bettingService.placeBet(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not open");

        // Ensure no bet persisted
        assertThat(betRepository.findByEventId(eventId)).isEmpty();
    }

    @Test
    void placeBet_onSettledEvent_shouldFail() {
        // Arrange
        User user = userRepository.findAll().get(0);
        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long driverId = (long) faker.number().numberBetween(1, 99);

        // Seed event as SETTLED
        HistoricalEvent he = new HistoricalEvent();
        he.setEventId(eventId);
        he.setEventName("Race-" + faker.lorem().word());
        he.setCountry(faker.country().name());
        he.setStatus(EventStatus.SETTLED);
        historicalEventRepository.save(he);

        // Mock event-service
        Driver driver = Driver.builder().driverNumber(driverId).fullName(faker.name().fullName()).teamName("T").odds(3).build();
        EventDetails ed = EventDetails.builder().sessionKey(eventId).sessionName("Settled").countryName("X").driver(driver).build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class)).willReturn(ed);

        PlaceBetRequest req = new PlaceBetRequest(user.getId(), eventId, driverId, 5L);

        // Act + Assert
        assertThatThrownBy(() -> bettingService.placeBet(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not open");

        // Ensure no bet persisted
        assertThat(betRepository.findByEventId(eventId)).isEmpty();
    }

    @Test
    void placeBet_whenAmountExceedsBalance_shouldFail_andNotChangeBalanceOrPersistBet() {
        // Arrange
        User user = userRepository.findAll().get(0);
        long starting = user.getBalanceEur();

        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long driverId = (long) faker.number().numberBetween(1, 99);

        // Mock event-service and ensure event OPEN (insertIfNotExists will create row)
        Driver driver = Driver.builder().driverNumber(driverId).fullName(faker.name().fullName()).teamName("T").odds(2).build();
        EventDetails ed = EventDetails.builder().sessionKey(eventId).sessionName("Race").countryName("X").driver(driver).build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class)).willReturn(ed);

        long tooMuch = starting + faker.number().numberBetween(1, 1000);
        PlaceBetRequest req = new PlaceBetRequest(user.getId(), eventId, driverId, tooMuch);

        // Act + Assert
        assertThatThrownBy(() -> bettingService.placeBet(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient balance");

        // Balance unchanged and no bet stored
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getBalanceEur()).isEqualTo(starting);
        assertThat(betRepository.findByEventId(eventId)).isEmpty();
    }

    @Test
    void placeBet_concurrentUpdates_onlyOneDebitPersists_andOtherFails() throws Exception {
        // Arrange
        User user = userRepository.findAll().get(0);
        long starting = user.getBalanceEur();

        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long driverId = (long) faker.number().numberBetween(1, 99);
        int odds = faker.number().numberBetween(2, 5);

        // Mock event-service with driver list containing the target driver
        Driver driver = Driver.builder().driverNumber(driverId).fullName(faker.name().fullName()).teamName("Team").odds(odds).build();
        EventDetails ed = EventDetails.builder()
                .sessionKey(eventId)
                .sessionName("Race-" + faker.lorem().word())
                .countryName(faker.country().name())
                .drivers(List.of(driver))
                .build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class)).willReturn(ed);

        long amount = Math.max(1, starting / 2 + 1); // more than half to avoid two successes

        PlaceBetRequest req1 = new PlaceBetRequest(user.getId(), eventId, driverId, amount);
        PlaceBetRequest req2 = new PlaceBetRequest(user.getId(), eventId, driverId, amount);

        ExecutorService exec = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();

        Callable<Object> task1 = () -> {
            startLatch.await(2, TimeUnit.SECONDS);
            try {
                return bettingService.placeBet(req1);
            } catch (Exception e) {
                return e;
            }
        };
        Callable<Object> task2 = () -> {
            startLatch.await(2, TimeUnit.SECONDS);
            try {
                return bettingService.placeBet(req2);
            } catch (Exception e) {
                return e;
            }
        };

        futures.add(exec.submit(task1));
        futures.add(exec.submit(task2));
        startLatch.countDown();

        Object r1 = futures.get(0).get(10, TimeUnit.SECONDS);
        Object r2 = futures.get(1).get(10, TimeUnit.SECONDS);
        exec.shutdownNow();

        // Assert: exactly one success, one IllegalStateException for insufficient balance
        int success = 0;
        int failures = 0;
        for (Object r : List.of(r1, r2)) {
            if (r instanceof BetResponse) success++;
            else if (r instanceof Exception ex) {
                assertThat(ex).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Insufficient balance");
                failures++;
            }
        }
        assertThat(success).isEqualTo(1);
        assertThat(failures).isEqualTo(1);

        // Final balance decreased by exactly one amount
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getBalanceEur()).isEqualTo(starting - amount);

        // Exactly one bet persisted for that user & event
        List<Bet> bets = betRepository.findByEventId(eventId);
        assertThat(bets).hasSize(1);
        assertThat(bets.get(0).getUser().getId()).isEqualTo(user.getId());
        assertThat(bets.get(0).getAmountEur()).isEqualTo(amount);
        assertThat(bets.get(0).getDriverId()).isEqualTo(driverId);
    }

    @Test
    void settleEvent_whenNoBets_shouldPersistOutcome_andSetEventSettled() {
        // Arrange: create an OPEN historical event without any bets
        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long winningDriverId = (long) faker.number().numberBetween(1, 99);

        HistoricalEvent he = new HistoricalEvent();
        he.setEventId(eventId);
        he.setEventName("Race-" + faker.lorem().word());
        he.setCountry(faker.country().name());
        he.setStatus(EventStatus.OPEN);
        historicalEventRepository.save(he);

        // Ensure no bets exist for this event
        assertThat(betRepository.findByEventId(eventId)).isEmpty();

        // Stub winner endpoint for settlement
        EventResult winner = EventResult.builder()
                .sessionKey(eventId)
                .finished(true)
                .winnerDriverNumber(winningDriverId)
                .build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId + "/winner", EventResult.class))
                .willReturn(winner);

        // Act: settle
        bettingService.settleEvent(eventId);

        // Assert: still no bets, but outcome persisted and event settled
        assertThat(betRepository.findByEventId(eventId)).isEmpty();
        EventOutcome outcome = eventOutcomeRepository.findById(eventId).orElseThrow();
        assertThat(outcome.getWinningDriverId()).isEqualTo(winningDriverId);
        assertThat(historicalEventRepository.findById(eventId).orElseThrow().getStatus()).isEqualTo(EventStatus.SETTLED);
    }

    private User newUser() {
        User u = new User();
        u.setUsername("user-" + faker.number().digits(6));
        u.setBalanceEur(10_000);
        return u;
    }
}
