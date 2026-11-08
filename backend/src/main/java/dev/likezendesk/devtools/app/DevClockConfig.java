package dev.likezendesk.devtools.app;

import dev.likezendesk.devtools.domain.AdjustableClock;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/** dev / e2e プロファイルでは、アプリケーションの時計をずらせる実装に差し替える（architecture.md 2.6）。 */
@Configuration(proxyBeanMethods = false)
@Profile({"dev", "e2e"})
class DevClockConfig {

    @Bean
    @Primary
    AdjustableClock adjustableClock() {
        return new AdjustableClock(Clock.systemUTC());
    }
}
