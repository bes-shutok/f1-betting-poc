package f1.betting.poc.provider.openf1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriverRawDto {

	private Long sessionKey;

	private Integer driverNumber;

	private String broadcastName;

	private String fullName;

	private String teamName;

	private String countryCode;
}
