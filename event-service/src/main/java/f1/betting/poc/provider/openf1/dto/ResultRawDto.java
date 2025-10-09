package f1.betting.poc.provider.openf1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResultRawDto {
	private Integer position;

	private Integer driverNumber;

	private String sessionKey;
}
