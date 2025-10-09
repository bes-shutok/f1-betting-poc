package f1.betting.poc.domain;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Driver {
	private int driverNumber;
	private String fullName;
	private String teamName;
	/**
	 * odds is an integer in {2,3,4} as per the POC rules.
	 * Provider adapter will set it (or mapper can leave it null/0 and adapter sets it).
	 */
	private int odds;
}
