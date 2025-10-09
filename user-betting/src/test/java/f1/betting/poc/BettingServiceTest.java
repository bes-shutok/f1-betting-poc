package f1.betting.poc;

import f1.betting.poc.domain.*;
import f1.betting.poc.web.BetResponse;
import f1.betting.poc.web.PlaceBetRequest;
import net.datafaker.Faker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BettingServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private HistoricalEventRepository historicalEventRepository;
    @Mock
    private BetRepository betRepository;
    @Mock
    private EventOutcomeRepository eventOutcomeRepository;
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private BettingService service;

    @Test
    void placeBet_shouldReturnResponseConsistentWithRequestAndComputedData() {
        // Arrange input
        Long userId = 10L;
        Long eventId = 200L;
        Long driverId = 44L;
        long amount = 25L; // measured in EUR (Long everywhere)

        PlaceBetRequest req = new PlaceBetRequest(userId, eventId, driverId, amount);

        // Event and driver coming from event-service
        Driver driver = Driver.builder()
                .driverNumber(driverId)
                .fullName("Lewis Hamilton")
                .teamName("Mercedes")
                .odds(3)
                .build();
        EventDetails event = EventDetails.builder()
                .sessionKey(eventId)
                .sessionName("British GP")
                .countryName("UK")
                .driver(driver)
                .build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class))
                .willReturn(event);

        // historical event row exists/open after insertIfNotExists
        HistoricalEvent he = new HistoricalEvent();
        he.setEventId(eventId);
        he.setEventName("British GP");
        he.setCountry("UK");
        he.setStatus(EventStatus.OPEN);
        given(historicalEventRepository.findById(eventId)).willReturn(Optional.of(he));

        // user and debit succeeds
        User user = new User();
        user.setId(userId);
        user.setUsername("john");
        user.setBalanceEur(1_000);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.debitUser(userId, amount)).willReturn(1);

        // save bet returns ID and echoes properties
        given(betRepository.save(any(Bet.class))).willAnswer(inv -> {
            Bet b = inv.getArgument(0);
            b.setId(777L);
            return b;
        });

        // Act
        BetResponse out = service.placeBet(req);

        // Assert: response corresponds with the request and computed fields
        assertThat(out).isNotNull();
        assertThat(out.betId()).isEqualTo(777L);
        assertThat(out.eventId()).isEqualTo(eventId);
        assertThat(out.driverId()).isEqualTo(driverId);
        // amountEur field name in DTO, but service returns amount in EUR; just assert equality to request amount (per current implementation)
        assertThat(out.amountEur()).isEqualTo(amount);
        assertThat(out.odds()).isEqualTo(driver.getOdds());
        assertThat(out.status()).isEqualTo(BetStatus.PENDING.name());
    }

    @Test
    void placeBet_whenAmountNotPositive_shouldThrow() {
        // Arrange minimal event/driver so service reaches amount validation
        Long eventId = 2L;
        Long driverId = 3L;
        PlaceBetRequest bad = new PlaceBetRequest(1L, eventId, driverId, 0L);

        Driver driver = Driver.builder()
                .driverNumber(driverId)
                .fullName("Test Driver")
                .teamName("Test Team")
                .odds(2)
                .build();
        EventDetails event = EventDetails.builder()
                .sessionKey(eventId)
                .sessionName("Any")
                .countryName("Any")
                .driver(driver)
                .build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class))
                .willReturn(event);

        // Act + Assert
        assertThatThrownBy(() -> service.placeBet(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void settleEvent_shouldPersistOutcomeAndUpdateBets_usingFaker() {
        // Arrange using Faker for realistic random but bounded values
        Faker faker = new Faker();
        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long winningDriverId = (long) faker.number().numberBetween(1, 99);

        // Event is OPEN initially
        HistoricalEvent he = new HistoricalEvent();
        he.setEventId(eventId);
        he.setStatus(EventStatus.OPEN);
        given(historicalEventRepository.findById(eventId)).willReturn(Optional.of(he));

        // Users
        User u1 = new User();
        u1.setId(1L);
        u1.setUsername("alice");
        u1.setBalanceEur(100L);

        User u2 = new User();
        u2.setId(2L);
        u2.setUsername("bob");
        u2.setBalanceEur(200L);

        // Bets: one winner, one loser
        Bet b1 = new Bet();
        b1.setUser(u1);
        b1.setEventId(eventId);
        b1.setDriverId(winningDriverId);
        b1.setAmountEur(10L);
        b1.setOdds(3);
        b1.setStatus(BetStatus.PENDING);

        Bet b2 = new Bet();
        b2.setUser(u2);
        b2.setEventId(eventId);
        b2.setDriverId(winningDriverId + 1); // ensure different
        b2.setAmountEur(5L);
        b2.setOdds(2);
        b2.setStatus(BetStatus.PENDING);

        given(betRepository.findByEventId(eventId)).willReturn(List.of(b1, b2));

        // Winner fetched from event-service
        EventResult winner = EventResult.builder().sessionKey(eventId).finished(true).winnerDriverNumber(winningDriverId).build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId + "/winner", EventResult.class))
                .willReturn(winner);

        // Echo saves
        given(betRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(eventOutcomeRepository.save(any(EventOutcome.class))).willAnswer(inv -> inv.getArgument(0));
        given(historicalEventRepository.save(any(HistoricalEvent.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        service.settleEvent(eventId);

        // Assert bets and balances
        assertThat(b1.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(b2.getStatus()).isEqualTo(BetStatus.LOST);
        assertThat(u1.getBalanceEur()).isEqualTo(100L + 10L * 3);
        assertThat(u2.getBalanceEur()).isEqualTo(200L);

        // Event outcome saved with correct values
        ArgumentCaptor<EventOutcome> outcomeCaptor = ArgumentCaptor.forClass(EventOutcome.class);
        verify(eventOutcomeRepository).save(outcomeCaptor.capture());
        EventOutcome savedOutcome = outcomeCaptor.getValue();
        assertThat(savedOutcome.getEventId()).isEqualTo(eventId);
        assertThat(savedOutcome.getWinningDriverId()).isEqualTo(winningDriverId);
        assertThat(savedOutcome.getSettledAt()).isNotNull();

        // Event status transitions to SETTLED and save invoked
        assertThat(he.getStatus()).isEqualTo(EventStatus.SETTLED);
        verify(historicalEventRepository).save(he);
        // Bets persisted
        verify(betRepository).saveAll(anyList());
    }
}
