package f1.betting.poc.provider.openf1;

import f1.betting.poc.domain.Driver;
import f1.betting.poc.domain.EventDetails;
import f1.betting.poc.domain.EventResult;
import f1.betting.poc.provider.openf1.dto.DriverRawDto;
import f1.betting.poc.provider.openf1.dto.SessionRawDto;
import f1.betting.poc.provider.openf1.dto.ResultRawDto;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import net.datafaker.Faker;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenF1ProviderAdapterTest {

    @Mock
    private OpenF1Mapper mapper;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private RateLimiterRegistry rateLimiterRegistry;
    @Mock
    private OpenF1CacheProxy cacheProxy;
    // We'll return a real RateLimiter instance from the registry stub to simplify behavior

    @InjectMocks
    private OpenF1ProviderAdapter adapter;

    @Captor
    private ArgumentCaptor<String> urlCaptor;

    @BeforeEach
    void setUp() {
        // Inject base URL used for building requests
        ReflectionTestUtils.setField(adapter, "baseUrl", "http://base");
        RateLimiterConfig cfg = RateLimiterConfig.custom()
                .limitForPeriod(1000)
                .limitRefreshPeriod(java.time.Duration.ofMillis(10))
                .timeoutDuration(java.time.Duration.ZERO)
                .build();
        RateLimiter realLimiter = RateLimiter.of("openf1", cfg);
        given(rateLimiterRegistry.rateLimiter("openf1")).willReturn(realLimiter);
    }

    @Test
    @DisplayName("Should call sessions endpoint and enrich events with drivers and odds")
    void getEventsShouldCallSessionsEndpointAndEnrichWithDrivers() {
        // Given
        Faker faker = new Faker();
        Long sessionKey1 = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Long sessionKey2 = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        String sessionName1 = "Session-" + faker.lorem().word();
        String sessionName2 = "Session-" + faker.lorem().word();
        String country = faker.country().name();
        String sessionType1 = "Type-" + faker.lorem().word();
        String sessionType2 = "Type-" + faker.lorem().word();
        int year = faker.number().numberBetween(1950, 2100);

        // Sessions from provider
        SessionRawDto s1 = new SessionRawDto();
        s1.setSessionKey(sessionKey1);
        s1.setSessionName(sessionName1);
        s1.setCountryName(country);
        s1.setSessionType(sessionType1);

        SessionRawDto s2 = new SessionRawDto();
        s2.setSessionKey(sessionKey2);
        s2.setSessionName(sessionName2);
        s2.setCountryName(country);
        s2.setSessionType(sessionType2);

        SessionRawDto[] sessions = {s1, s2};
        given(restTemplate.getForObject(anyString(), eq(SessionRawDto[].class))).willReturn(sessions);

        // Mapper for EventDetails (should reflect source values)
        EventDetails ed1 = EventDetails.builder()
                .sessionKey(sessionKey1)
                .sessionName(sessionName1)
                .countryName(country)
                .sessionType(sessionType1)
                .build();
        EventDetails ed2 = EventDetails.builder()
                .sessionKey(sessionKey2)
                .sessionName(sessionName2)
                .countryName(country)
                .sessionType(sessionType2)
                .build();
        given(mapper.toEventDetails(s1)).willReturn(ed1);
        given(mapper.toEventDetails(s2)).willReturn(ed2);

        // Drivers per session using Faker
        DriverRawDto d1 = new DriverRawDto();
        d1.setDriverNumber(faker.number().numberBetween(1, 99));
        d1.setFullName(faker.name().fullName());
        d1.setTeamName("Team-" + faker.team().name());

        DriverRawDto d2 = new DriverRawDto();
        d2.setDriverNumber(faker.number().numberBetween(1, 99));
        d2.setFullName(faker.name().fullName());
        d2.setTeamName("Team-" + faker.team().name());

        given(cacheProxy.getDriversForSession(sessionKey1)).willReturn(Arrays.asList(d1, d2));
        given(cacheProxy.getDriversForSession(sessionKey2)).willReturn(Collections.singletonList(d1));

        // Mapper for drivers -> domain drivers; odds will be assigned in adapter later
        Driver dd1 = Driver.builder().driverNumber(d1.getDriverNumber().longValue()).fullName(d1.getFullName()).teamName(d1.getTeamName()).odds(0).build();
        Driver dd2 = Driver.builder().driverNumber(d2.getDriverNumber().longValue()).fullName(d2.getFullName()).teamName(d2.getTeamName()).odds(0).build();
        given(mapper.toDriverList(Arrays.asList(d1, d2))).willReturn(Arrays.asList(dd1, dd2));
        given(mapper.toDriverList(Collections.singletonList(d1))).willReturn(Collections.singletonList(dd1));

        // When
        List<EventDetails> out = adapter.getEvents(sessionType1, country, year);

        // Then
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getSessionKey()).isEqualTo(sessionKey1);
        assertThat(out.get(1).getSessionKey()).isEqualTo(sessionKey2);

        // Odds must be between 2 and 4 inclusive
        assertThat(out.get(0).getDrivers()).isNotEmpty();
        out.forEach(ed -> ed.getDrivers().forEach(dr -> assertThat(dr.getOdds()).isBetween(2, 4)));

        // Verify sessions endpoint URL includes dynamic filters
        verify(restTemplate).getForObject(urlCaptor.capture(), eq(SessionRawDto[].class));
        String calledUrl = urlCaptor.getValue();
        assertThat(calledUrl).startsWith("http://base/sessions?");
        assertThat(calledUrl).contains("session_type=" + sessionType1);
        assertThat(calledUrl).contains("country_name=" + country);
        assertThat(calledUrl).contains("year=" + year);
    }

    @Test
    @DisplayName("Should return empty list when remote API returns null")
    void getEventsShouldReturnEmptyListWhenRemoteReturnsNull() {
        // Given
        given(restTemplate.getForObject(anyString(), eq(SessionRawDto[].class))).willReturn(null);

        // When
        List<EventDetails> out = adapter.getEvents(null, null, null);

        // Then
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("Should return winner for session key with position 1 as winner")
    void getWinnerShouldReturnWinnerForRandomSessionKey() {
        // Given
        Faker faker = new Faker();
        Long randomSessionKey = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);

        ResultRawDto r1 = new ResultRawDto();
        r1.setPosition(2);
        r1.setDriverNumber(81L);
        r1.setSessionKey(randomSessionKey);

        ResultRawDto r2 = new ResultRawDto();
        r2.setPosition(1);
        r2.setDriverNumber(1L);
        r2.setSessionKey(randomSessionKey);

        ResultRawDto[] results = {r1, r2};
        given(restTemplate.getForObject(anyString(), eq(ResultRawDto[].class))).willReturn(results);

        OffsetDateTime beforeCall = OffsetDateTime.now().minusMinutes(1);

        // When
        Optional<EventResult> maybe = adapter.getWinner(randomSessionKey);

        // Then
        assertThat(maybe).isPresent();
        EventResult er = maybe.get();
        assertThat(er.getSessionKey()).isEqualTo(randomSessionKey);
        assertThat(er.isFinished()).isTrue();
        assertThat(er.getWinnerDriverNumber()).isEqualTo(1);
        assertThat(er.getProviderFetchedAt()).isNotNull();
        assertThat(er.getProviderFetchedAt()).isAfter(beforeCall);

        // Verify URL uses the dynamic key
        verify(restTemplate).getForObject(urlCaptor.capture(), eq(ResultRawDto[].class));
        String calledUrl = urlCaptor.getValue();
        assertThat(calledUrl).isEqualTo("http://base/position?session_key=" + randomSessionKey);
    }

    @Test
    @DisplayName("Should return empty optional when no race results are available")
    void getWinnerShouldReturnEmptyWhenNoData() {
        // Given null response
        given(restTemplate.getForObject(anyString(), eq(ResultRawDto[].class))).willReturn(null);

        // When & Then
        assertThat(adapter.getWinner(9999L)).isEmpty();

        // Given empty array response
        given(restTemplate.getForObject(anyString(), eq(ResultRawDto[].class))).willReturn(new ResultRawDto[]{});

        // When & Then
        assertThat(adapter.getWinner(9999L)).isEmpty();
    }

    @Test
    @DisplayName("Should call single session endpoint and enrich with drivers and odds")
    void getEventShouldCallSingleSessionEndpointAndEnrichDrivers() {
        // Given
        Faker faker = new Faker();
        Long sessionKey = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        String sessionName = "Session-" + faker.lorem().word();
        String country = faker.country().name();
        String sessionType = "Type-" + faker.lorem().word();

        // Provider returns a single session object
        SessionRawDto s = new SessionRawDto();
        s.setSessionKey(sessionKey);
        s.setSessionName(sessionName);
        s.setCountryName(country);
        s.setSessionType(sessionType);
        given(restTemplate.getForObject(anyString(), eq(SessionRawDto[].class))).willReturn(new SessionRawDto[]{s});

        // Mapper for EventDetails
        EventDetails ed = EventDetails.builder()
                .sessionKey(sessionKey)
                .sessionName(sessionName)
                .countryName(country)
                .sessionType(sessionType)
                .build();
        given(mapper.toEventDetails(s)).willReturn(ed);

        // Drivers via cache proxy
        DriverRawDto d1 = new DriverRawDto();
        d1.setDriverNumber(faker.number().numberBetween(1, 99));
        d1.setFullName(faker.name().fullName());
        d1.setTeamName("Team-" + faker.team().name());

        DriverRawDto d2 = new DriverRawDto();
        d2.setDriverNumber(faker.number().numberBetween(1, 99));
        d2.setFullName(faker.name().fullName());
        d2.setTeamName("Team-" + faker.team().name());

        given(cacheProxy.getDriversForSession(sessionKey)).willReturn(Arrays.asList(d1, d2));

        Driver dd1 = Driver.builder().driverNumber(d1.getDriverNumber().longValue()).fullName(d1.getFullName()).teamName(d1.getTeamName()).odds(0).build();
        Driver dd2 = Driver.builder().driverNumber(d2.getDriverNumber().longValue()).fullName(d2.getFullName()).teamName(d2.getTeamName()).odds(0).build();
        given(mapper.toDriverList(Arrays.asList(d1, d2))).willReturn(Arrays.asList(dd1, dd2));

        // When
        EventDetails out = adapter.getEvent(sessionKey);

        // Then
        assertThat(out).isNotNull();
        assertThat(out.getSessionKey()).isEqualTo(sessionKey);
        assertThat(out.getDrivers()).hasSize(2);
        out.getDrivers().forEach(dr -> assertThat(dr.getOdds()).isBetween(2, 4));

        // Verify URL formation
        verify(restTemplate).getForObject(urlCaptor.capture(), eq(SessionRawDto[].class));
        String calledUrl = urlCaptor.getValue();
        assertThat(calledUrl).isEqualTo("http://base/sessions?session_key=" + sessionKey);
    }
}
