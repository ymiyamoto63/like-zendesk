package dev.likezendesk.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** メールを SMTP に送らず、{@link RecordingMailSender} に記録する。 */
@TestConfiguration(proxyBeanMethods = false)
public class MailTestConfiguration {

    @Bean
    @Primary
    RecordingMailSender recordingMailSender() {
        return new RecordingMailSender();
    }
}
