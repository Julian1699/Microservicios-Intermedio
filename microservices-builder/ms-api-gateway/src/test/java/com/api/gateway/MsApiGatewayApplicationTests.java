package com.api.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MsApiGatewayApplicationTests.PermitAllSecurity.class)
class MsApiGatewayApplicationTests {

	/**
	 * Sin Keycloak en CI: cadena de seguridad que permite todo para que el contexto arranque.
	 * El perfil {@code test} desactiva {@link com.api.gateway.config.SecurityConfig} ({@code @Profile("!test")}).
	 */
	@TestConfiguration
	@EnableWebSecurity
	static class PermitAllSecurity {

		@Bean
		SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
			http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(
			auth -> auth.anyRequest().permitAll());
			return http.build();
		}

	}

	@Test
	void contextLoads() {
	}

}
