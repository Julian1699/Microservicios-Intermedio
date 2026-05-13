package com.api.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 Resource Server (JWT) validado contra Keycloak.
 * <p>
 * El gateway es <strong>WebMVC</strong> (no WebFlux): se usa {@link HttpSecurity}, no {@code ServerHttpSecurity}.
 */
@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
		httpSecurity
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/eureka/**")
				.permitAll()
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
		return httpSecurity.build();
	}

}
