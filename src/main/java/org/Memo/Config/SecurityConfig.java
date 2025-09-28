package org.Memo.Config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Value("${app.security.disabled:true}")
    private boolean securityDisabled;

    private final JwtAuthFilter jwtAuthFilter;
    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .headers(h -> h.frameOptions(f -> f.disable()));

        if (securityDisabled) {
            // 开发/联调：全部放行
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            // 回滚到原先策略（示例）
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/auth/wx/login", "/h2-console/**", "/health").permitAll()
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(jwtAuthFilter, AnonymousAuthenticationFilter.class)
                    .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }
}
