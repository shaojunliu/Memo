package org.Memo.Config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // H2 Console 需要关闭 CSRF（开发环境）
                .csrf(csrf -> csrf.disable())

                // 放行登录与 H2 Console，其余请求需要认证
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/wx/login", "/h2-console/**"
                                ,"/health" ).permitAll()
                        .anyRequest().authenticated()
                )

                // 允许 H2 Console 的 <frame>，不然页面会空白/被拦
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                // 如果只想同源允许也可以：.headers(h -> h.frameOptions().sameOrigin())

                // 你的 JWT 过滤器负责解析 token 并写入 SecurityContext
                .addFilterBefore(jwtAuthFilter, AnonymousAuthenticationFilter.class)

                // 如无 Basic 认证需求可以去掉；保留也无妨
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
