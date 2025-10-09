package f1.betting.poc.domain;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDetails {
	private Long sessionKey;
	private String sessionName;
	private String countryName;
	private OffsetDateTime dateStart;
	private OffsetDateTime dateEnd;
	private Integer year;
	private String sessionType;

	/**
	 * Use @Singular so MapStruct / builder usage can be convenient:
	 * EventDetails.builder().driver(driver1).driver(driver2).build()
	 */
	@Singular("driver")
	private List<Driver> drivers;
}
