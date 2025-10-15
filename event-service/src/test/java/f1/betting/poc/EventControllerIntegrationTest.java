package f1.betting.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import f1.betting.poc.domain.Driver;
import f1.betting.poc.domain.EventDetails;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean EventService eventService; // isolate from provider/external API

    Faker faker;

    @BeforeEach
    void setup() {
        faker = new Faker();
    }

    @Test
    @DisplayName("Should return paged response with events and driver odds")
    void getEventsShouldReturnPagedEnvelopeAndItems() throws Exception {
        // Given
        EventDetails e1 = EventDetails.builder()
                .sessionKey((long) faker.number().numberBetween(1, Integer.MAX_VALUE))
                .sessionName("Race-" + faker.lorem().word())
                .countryName(faker.country().name())
                .driver(Driver.builder().driverNumber(1L).fullName("D1").teamName("T1").odds(3).build())
                .build();
        EventDetails e2 = EventDetails.builder()
                .sessionKey((long) faker.number().numberBetween(1, Integer.MAX_VALUE))
                .sessionName("Race-" + faker.lorem().word())
                .countryName(faker.country().name())
                .driver(Driver.builder().driverNumber(2L).fullName("D2").teamName("T2").odds(2).build())
                .build();
        given(eventService.getEvents(any(), any(), any(), anyInt(), anyInt())).willReturn(List.of(e1, e2));

        // When
        var result = mockMvc.perform(get("/api/events?page=0&size=2"));

        // Then
        result
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].session_key", is(notNullValue())))
                .andExpect(jsonPath("$.items[0].drivers", not(empty())))
                .andExpect(jsonPath("$.items[0].drivers[0].odds", anyOf(is(2), is(3), is(4))));
    }

    @Test
    @DisplayName("Should return 200 with single event details and drivers")
    void getEventShouldReturn200WithEventDetails() throws Exception {
        // Given
        Long sessionKey = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        Driver d = Driver.builder().driverNumber(1L).fullName(faker.name().fullName()).teamName("T").odds(3).build();
        EventDetails ed = EventDetails.builder()
                .sessionKey(sessionKey)
                .sessionName("Race-" + faker.lorem().word())
                .countryName(faker.country().name())
                .driver(d)
                .build();
        given(eventService.getEvent(sessionKey)).willReturn(ed);

        // When
        var result = mockMvc.perform(get("/api/events/" + sessionKey));

        // Then
        result
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.session_key").value(sessionKey))
                .andExpect(jsonPath("$.drivers[0].full_name", is(d.getFullName())));
    }

    @Test
    @DisplayName("Should return 404 when winner not found for session")
    void getWinnerShouldReturn404WhenNotFound() throws Exception {
        // Given
        Long sessionKey = (long) faker.number().numberBetween(1, Integer.MAX_VALUE);
        given(eventService.getWinner(sessionKey)).willReturn(Optional.empty());

        // When
        var result = mockMvc.perform(get("/api/events/" + sessionKey + "/winner"));

        // Then
        result
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 400 when service throws IllegalArgumentException")
    void getEventShouldReturn400WhenServiceThrowsIllegalArgument() throws Exception {
        // Given
        Long badKey = -1L;
        given(eventService.getEvent(badKey)).willThrow(new IllegalArgumentException("Invalid session key"));

        // When
        var result = mockMvc.perform(get("/api/events/" + badKey));

        // Then
        result
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Invalid session key")));
    }
}
