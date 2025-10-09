package f1.betting.poc.provider.openf1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Only contains fields we care about. Unknown properties are ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionRawDto {
	private String meetingKey;

	private String sessionKey;

	private String location;

	private String sessionType;

	private String sessionName;

	private String countryName;

	private String circuitShortName;

	private String dateStart;

	private String dateEnd;

	private Integer year;
}
