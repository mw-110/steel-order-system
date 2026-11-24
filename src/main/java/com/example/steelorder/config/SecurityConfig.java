package com.example.steelorder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/js/**", "/h2-console/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/orders/**", "/api/v1/orders/**")
                        .hasAnyRole("SALES", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/orders/**", "/api/v1/orders/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/arrivals/**", "/shipments/**", "/api/v1/arrivals/**")
                        .hasAnyRole("WAREHOUSE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/arrivals/**", "/shipments/**",
                                "/api/v1/arrivals/**", "/api/v1/inventories/**")
                        .authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());

        http.csrf(csrf -> csrf
                .ignoringRequestMatchers("/h2-console/**"));
        http.headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }
}
