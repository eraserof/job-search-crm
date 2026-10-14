package com.jobcrm.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the application's {@link ObjectMapper} bean. This is a CLI app on the plain {@code
 * spring-boot-starter} (no web), so Boot's Jackson auto-configuration never runs — we provide the
 * mapper explicitly for the repositories that serialize/deserialize JSON columns.
 */
@Configuration
public class JacksonConfig {

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
