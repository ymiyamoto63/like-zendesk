package dev.likezendesk;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security は CSRF 対策とセキュリティヘッダーにだけ使う。
 *
 * <p>認証と認可は、セッションに保存したテナントごとのログイン状態から {@code Viewer} を作って行う（identity.web.ViewerArgumentResolver）。
 * そのため、ここでは全てのリクエストを通す。
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // XSRF-TOKEN Cookie を発行し、状態を変えるリクエストに X-XSRF-TOKEN ヘッダーを要求する
                .csrf(CsrfConfigurer::spa)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .anonymous(anonymous -> anonymous.disable())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
