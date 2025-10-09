package f1.betting.poc.provider.openf1;

import f1.betting.poc.domain.Driver;
import f1.betting.poc.domain.EventDetails;
import f1.betting.poc.provider.openf1.dto.DriverRawDto;
import f1.betting.poc.provider.openf1.dto.SessionRawDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Mapper(componentModel = "spring")
public interface OpenF1Mapper {

	@Mapping(source = "sessionKey", target = "sessionKey")
	@Mapping(source = "sessionName", target = "sessionName")
	@Mapping(source = "countryName", target = "countryName")
	@Mapping(source = "sessionType", target = "sessionType")
	@Mapping(source = "dateStart", target = "dateStart", qualifiedByName = "parseOffsetDateTime")
	@Mapping(source = "dateEnd", target = "dateEnd", qualifiedByName = "parseOffsetDateTime")
	@Mapping(target = "drivers", ignore = true) // populated manually after fetching
	@Mapping(target = "driver", ignore = true) // populated manually after fetching
	EventDetails toEventDetails(SessionRawDto dto);

	List<EventDetails> toEventDetailsList(List<SessionRawDto> dtos);

	@Mapping(source = "driverNumber", target = "driverNumber")
	@Mapping(source = "fullName", target = "fullName")
	@Mapping(source = "teamName", target = "teamName")
	@Mapping(target = "odds", ignore = true) // adapter will set odds (random) later
	Driver toDriver(DriverRawDto dto);

	List<Driver> toDriverList(List<DriverRawDto> dtos);

	@Named("parseOffsetDateTime")
	default OffsetDateTime parseOffsetDateTime(String s) {
		if (s == null) return null;
		// OpenF1 returns ISO_OFFSET_DATE_TIME (ex: 2023-07-29T15:05:00+00:00)
		return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}
}

