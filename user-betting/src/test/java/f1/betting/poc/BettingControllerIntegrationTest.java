package f1.betting.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import f1.betting.poc.domain.*;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
@Transactional
class BettingControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BetRepository betRepository;
    @Autowired HistoricalEventRepository historicalEventRepository;
    @Autowired EventOutcomeRepository eventOutcomeRepository;
    @Autowired ObjectMapper objectMapper; // configured to SNAKE_CASE in AppConfig

    @MockitoBean RestTemplate restTemplate; // mock external event-service

    Faker faker = new Faker();

    @BeforeEach
    void setup() {
    }

    @Test
    @DisplayName("Should create bet and debit user balance")
    void createBetShouldDebitUser() throws Exception {
        // Given
        User user = userRepository.findAll().get(0);
        long starting = user.getBalanceEur();

        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long driverId = (long) faker.number().numberBetween(1, 99);
        int odds = faker.number().numberBetween(2, 5);
        long amount = faker.number().numberBetween(1, 50);

        Driver driver = Driver.builder().driverNumber(driverId).fullName(faker.name().fullName()).teamName("T").odds(odds).build();
        EventDetails ed = EventDetails.builder()
                .sessionKey(eventId)
                .sessionName("Race-" + faker.lorem().word())
                .countryName(faker.country().name())
                .driver(driver) // minimal valid list containing driver
                .build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class))
                .willReturn(ed);

        var payload = new java.util.LinkedHashMap<String,Object>();
        payload.put("user_id", user.getId());
        payload.put("event_id", eventId);
        payload.put("driver_id", driverId);
        payload.put("amount_eur", amount);
        String json = objectMapper.writeValueAsString(payload);

        // When
        var mvcResult = mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value(eventId))
                .andExpect(jsonPath("$.driver_id").value(driverId))
                .andExpect(jsonPath("$.amount_eur").value((int) amount))
                .andExpect(jsonPath("$.odds").value(odds))
                .andExpect(jsonPath("$.status").value(BetStatus.PENDING.name()))
                .andReturn();

        // Then
        String body = mvcResult.getResponse().getContentAsString();
        Long betId = ( objectMapper.readTree(body).get("bet_id").numberValue()).longValue();

        Bet stored = betRepository.findById(betId).orElseThrow();
        assertThat(stored.getUser().getId()).isEqualTo(user.getId());
        assertThat(stored.getEventId()).isEqualTo(eventId);
        assertThat(stored.getDriverId()).isEqualTo(driverId);
        assertThat(stored.getAmountEur()).isEqualTo(amount);

        long newBalance = userRepository.findById(user.getId()).orElseThrow().getBalanceEur();
        assertThat(newBalance).isEqualTo(starting - amount);
        assertThat(historicalEventRepository.findById(eventId)).isPresent();
    }

    @Test
    @DisplayName("Should settle event and update bets, balances, and outcome")
    void settleEventShouldUpdateBalances() throws Exception {
        // Given
        List<User> users = userRepository.findAll();
        User u1 = users.get(0);
        User u2 = users.size() > 1 ? users.get(1) : userRepository.save(newUser());
        long start1 = u1.getBalanceEur();
        long start2 = u2.getBalanceEur();

        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long winDriver = (long) faker.number().numberBetween(1, 99);
        Long loseDriver = winDriver + 1;

        Driver dWin = Driver.builder().driverNumber(winDriver).fullName(faker.name().fullName()).teamName("T1").odds(3).build();
        Driver dLose = Driver.builder().driverNumber(loseDriver).fullName(faker.name().fullName()).teamName("T2").odds(2).build();
        EventDetails ed = EventDetails.builder()
                .sessionKey(eventId)
                .sessionName("Race-" + faker.lorem().word())
                .countryName(faker.country().name())
                .drivers(java.util.List.of(dWin, dLose))
                .build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class))
                .willReturn(ed);

        placeBetApi(u1.getId(), eventId, winDriver, 10L);
        placeBetApi(u2.getId(), eventId, loseDriver, 5L);

        EventResult winner = EventResult.builder().sessionKey(eventId).finished(true).winnerDriverNumber(winDriver).build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId + "/winner", EventResult.class))
                .willReturn(winner);

        // When
        mockMvc.perform(post("/api/events/" + eventId + "/settle"))
                .andExpect(status().isOk());

        // Then
        List<Bet> bets = betRepository.findByEventId(eventId);
        assertThat(bets).hasSize(2);
        Bet b1 = bets.stream().filter(b -> b.getUser().getId().equals(u1.getId())).findFirst().orElseThrow();
        Bet b2 = bets.stream().filter(b -> b.getUser().getId().equals(u2.getId())).findFirst().orElseThrow();
        assertThat(b1.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(b2.getStatus()).isEqualTo(BetStatus.LOST);

        User r1 = userRepository.findById(u1.getId()).orElseThrow();
        User r2 = userRepository.findById(u2.getId()).orElseThrow();

        // Total pool: 10 (u1) + 5 (u2) = 15 EUR
        // u1's proportional share: 10/15 = 66.67% of pool
        // u1 should receive: 15 EUR total (10 back + 5 winnings)
        long expectedU1Balance = start1 - 10L + 15L;
        assertThat(r1.getBalanceEur()).isEqualTo(expectedU1Balance);
        assertThat(r2.getBalanceEur()).isEqualTo(start2 - 5L);

        assertThat(eventOutcomeRepository.findById(eventId)).isPresent();
        assertThat(historicalEventRepository.findById(eventId).orElseThrow().getStatus()).isEqualTo(EventStatus.SETTLED);
    }

    @Test
    @DisplayName("Should distribute pool proportionally among multiple winners with rounding down")
    void settleEventShouldDistributePool() throws Exception {
        // Given
        List<User> users = userRepository.findAll();
        User u1 = users.get(0);
        User u2 = users.size() > 1 ? users.get(1) : userRepository.save(newUser());
        User u3 = users.size() > 2 ? users.get(2) : userRepository.save(newUser());

        long start1 = u1.getBalanceEur();
        long start2 = u2.getBalanceEur();
        long start3 = u3.getBalanceEur();

        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long winDriver = (long) faker.number().numberBetween(1, 99);
        Long loseDriver = winDriver + 1;

        Driver dWin = Driver.builder().driverNumber(winDriver).fullName(faker.name().fullName()).teamName("T1").odds(2).build();
        Driver dLose = Driver.builder().driverNumber(loseDriver).fullName(faker.name().fullName()).teamName("T2").odds(3).build();
        EventDetails ed = EventDetails.builder()
                .sessionKey(eventId)
                .sessionName("Race-" + faker.lorem().word())
                .countryName(faker.country().name())
                .drivers(java.util.List.of(dWin, dLose))
                .build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class))
                .willReturn(ed);

        // When: Place bets - all 3 users bet on winning driver to test proportional distribution
        placeBetApi(u1.getId(), eventId, winDriver, 7L);  // 7 EUR bet
        placeBetApi(u2.getId(), eventId, winDriver, 3L);  // 3 EUR bet
        placeBetApi(u3.getId(), eventId, loseDriver, 5L); // 5 EUR bet on loser

        // Stub winner endpoint for settlement
        EventResult winner = EventResult.builder().sessionKey(eventId).finished(true).winnerDriverNumber(winDriver).build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId + "/winner", EventResult.class))
                .willReturn(winner);

        // Act: Settle the event
        mockMvc.perform(post("/api/events/" + eventId + "/settle"))
                .andExpect(status().isOk());

        // Then: Verify proportional distribution
        List<Bet> bets = betRepository.findByEventId(eventId);
        assertThat(bets).hasSize(3);

        User r1 = userRepository.findById(u1.getId()).orElseThrow();
        User r2 = userRepository.findById(u2.getId()).orElseThrow();
        User r3 = userRepository.findById(u3.getId()).orElseThrow();

        // Total pool: 7 + 3 + 5 = 15 EUR
        // Winners' bets: 7 + 3 = 10 EUR
        // u1's share: 7/10 = 70% of pool -> should get 15 * 0.7 = 10.5 -> rounded down to 10 EUR
        // u2's share: 3/10 = 30% of pool -> should get 15 * 0.3 = 4.5 -> rounded down to 4 EUR
        // Remaining 1 EUR (15 - 10 - 4) is lost due to rounding down

        long expectedU1Balance = start1 - 7L + 10L; // 7 back + 3 winnings
        long expectedU2Balance = start2 - 3L + 4L;  // 3 back + 1 winnings
        long expectedU3Balance = start3 - 5L;        // loses bet

        assertThat(r1.getBalanceEur()).isEqualTo(expectedU1Balance);
        assertThat(r2.getBalanceEur()).isEqualTo(expectedU2Balance);
        assertThat(r3.getBalanceEur()).isEqualTo(expectedU3Balance);

        assertThat(eventOutcomeRepository.findById(eventId)).isPresent();
        assertThat(historicalEventRepository.findById(eventId).orElseThrow().getStatus()).isEqualTo(EventStatus.SETTLED);
    }

    @Test
    @DisplayName("Should fail when bet amount exceeds user balance")
    void createBetShouldRejectInsufficientFunds() throws Exception {
        // Given
        User user = userRepository.findAll().get(0);
        long starting = user.getBalanceEur();
        Long eventId = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long driverId = (long) faker.number().numberBetween(1, 99);

        Driver driver = Driver.builder().driverNumber(driverId).fullName(faker.name().fullName()).teamName("T").odds(2).build();
        EventDetails ed = EventDetails.builder().sessionKey(eventId).sessionName("Race").countryName("X").driver(driver).build();
        given(restTemplate.getForObject("http://localhost:8081/api/events/" + eventId, EventDetails.class))
                .willReturn(ed);

        long tooMuch = starting + faker.number().numberBetween(1, 1_000);
        var payload = new java.util.LinkedHashMap<String,Object>();
        payload.put("user_id", user.getId());
        payload.put("event_id", eventId);
        payload.put("driver_id", driverId);
        payload.put("amount_eur", tooMuch);
        String json = objectMapper.writeValueAsString(payload);

        // When
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        // Then
        assertThat(betRepository.findByEventId(eventId)).isEmpty();
        long after = userRepository.findById(user.getId()).orElseThrow().getBalanceEur();
        assertThat(after).isEqualTo(starting);
    }

    private void placeBetApi(Long userId, Long eventId, Long driverId, Long amount) throws Exception {
        var payload = new java.util.LinkedHashMap<String,Object>();
        payload.put("user_id", userId);
        payload.put("event_id", eventId);
        payload.put("driver_id", driverId);
        payload.put("amount_eur", amount);
        String json = objectMapper.writeValueAsString(payload);
        mockMvc.perform(post("/api/bets").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private User newUser() {
        User u = new User();
        u.setUsername("user-" + faker.number().digits(6));
        u.setBalanceEur(100);
        return u;
    }
}
