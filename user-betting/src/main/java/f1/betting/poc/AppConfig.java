package f1.betting.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.setPropertyNamingStrategy( PropertyNamingStrategies.SNAKE_CASE);
		mapper.registerModules(new JavaTimeModule(), new ParameterNamesModule());
		return mapper;
	}

	@Bean
	public RestTemplate restTemplate(ObjectMapper objectMapper) {
		// Make RestTemplate use the customized ObjectMapper
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.getMessageConverters().stream()
				.filter(c -> c instanceof MappingJackson2HttpMessageConverter )
				.map(c -> (MappingJackson2HttpMessageConverter) c)
				.forEach(c -> c.setObjectMapper(objectMapper));
		return restTemplate;
	}
}
