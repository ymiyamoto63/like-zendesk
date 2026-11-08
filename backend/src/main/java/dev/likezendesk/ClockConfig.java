package dev.likezendesk;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 現在時刻は必ずこの Bean から取る。e2e プロファイルでは時刻を進められる実装に差し替える。 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
