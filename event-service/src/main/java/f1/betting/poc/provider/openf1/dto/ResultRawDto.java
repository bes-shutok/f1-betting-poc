package f1.betting.poc.provider.openf1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResultRawDto {
	private Integer position;

	private Long driverNumber;

	private Long sessionKey;
}
