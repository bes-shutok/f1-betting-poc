package f1.betting.poc.provider.openf1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Only contains fields we care about. Unknown properties are ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionRawDto {

	private Long sessionKey;

	private String location;

	private String sessionType;

	private String sessionName;

	private String countryName;

	private String dateStart;

	private String dateEnd;

	private Integer year;
}
