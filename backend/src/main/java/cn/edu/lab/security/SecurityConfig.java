package cn.edu.lab.security;

import cn.edu.lab.common.*;
import cn.edu.lab.user.service.UserService;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain chain(
      HttpSecurity http,
      JwtService jwt,
      UserService users,
      SecurityJson errors,
      CorsConfigurationSource corsConfigurationSource,
      com.fasterxml.jackson.databind.ObjectMapper json)
      throws Exception {
    var cors = new org.springframework.web.filter.CorsFilter(corsConfigurationSource);
    cors.setCorsProcessor(
        new DefaultCorsProcessor() {
          @Override
          protected void rejectRequest(org.springframework.http.server.ServerHttpResponse response)
              throws java.io.IOException {
            response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
            response
                .getHeaders()
                .setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            json.writeValue(response.getBody(), ApiResponse.error(ErrorCode.FORBIDDEN));
            response.flush();
          }
        });
    http.csrf(c -> c.disable())
        .cors(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .logout(c -> c.disable())
        .exceptionHandling(
            c ->
                c.authenticationEntryPoint((q, s, e) -> errors.write(s, ErrorCode.UNAUTHORIZED))
                    .accessDeniedHandler((q, s, e) -> errors.write(s, ErrorCode.FORBIDDEN)))
        .authorizeHttpRequests(
            c ->
                c.requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/v1/auth/login",
                        "/api/v1/auth/wechat/login",
                        "/api/v1/auth/wechat/bind")
                    .permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/health")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/me")
                    .authenticated()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET, "/api/v1/reservations/mine")
                    .hasRole("STUDENT")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET, "/api/v1/reservations")
                    .hasAnyRole("LAB_ADMIN", "SYS_ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET, "/api/v1/reservations/*")
                    .authenticated()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/v1/reservations")
                    .hasRole("STUDENT")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/v1/reservations/*/cancel")
                    .hasRole("STUDENT")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/v1/reservations/*/review",
                        "/api/v1/reservations/*/revoke",
                        "/api/v1/reservations/*/complete")
                    .hasRole("LAB_ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET, "/api/v1/statistics/overview")
                    .hasAnyRole("LAB_ADMIN", "SYS_ADMIN")
                    .requestMatchers("/api/v1/users/**")
                    .hasRole("SYS_ADMIN")
                    .requestMatchers("/api/v1/labs/*/admins")
                    .hasRole("SYS_ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/v1/labs",
                        "/api/v1/labs/**",
                        "/api/v1/equipment",
                        "/api/v1/equipment/**")
                    .authenticated()
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/labs")
                    .hasRole("SYS_ADMIN")
                    .requestMatchers("/api/v1/labs/**", "/api/v1/equipment/**")
                    .hasAnyRole("LAB_ADMIN", "SYS_ADMIN")
                    .anyRequest()
                    .denyAll())
        .addFilterAt(cors, org.springframework.web.filter.CorsFilter.class)
        .addFilterBefore(new TraceFilter(), SecurityContextHolderFilter.class)
        .addFilterBefore(
            new JwtFilter(jwt, users, errors), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins}") String origins) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(
        Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    if (config.getAllowedOrigins().contains("*"))
      throw new IllegalArgumentException("CORS 必须配置明确来源");
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
    config.setExposedHeaders(List.of("X-Request-ID"));
    config.setAllowCredentials(false);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
