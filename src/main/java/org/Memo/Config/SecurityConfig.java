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
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/wx").disable())
                .headers(h -> h.frameOptions(f -> f.disable()));

        if (securityDisabled) {
            // 开发/联调：全部放行
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            // 回滚到原先策略（示例）
            http.authorizeHttpRequests(auth -> auth
                            // 微信服务器入口（必须放行）
                            .requestMatchers("/wx").permitAll()
                            // 如果你还有 /wx/** 其他子路由，也一并放行
                            .requestMatchers("/wx/**").permitAll()
                            .requestMatchers("/api/auth/wx/login", "/h2-console/**", "/health").permitAll()
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(jwtAuthFilter, AnonymousAuthenticationFilter.class)
                    .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }
}
