package dev.likezendesk.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

/** 送ったメールを覚えておく MailSender。{@link MailTestConfiguration} が登録する。 */
public class RecordingMailSender implements MailSender {

    private static final Pattern LINK = Pattern.compile("https?://\\S+/([A-Za-z0-9_-]{20,})");

    private final List<SimpleMailMessage> sent = new ArrayList<>();

    @Override
    public synchronized void send(SimpleMailMessage... messages) {
        sent.addAll(List.of(messages));
    }

    /** {@code to} 宛ての最後のメール。 */
    public synchronized SimpleMailMessage lastTo(String to) {
        return sent.reversed().stream()
                .filter(m -> List.of(m.getTo()).contains(to))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No mail to " + to));
    }

    public synchronized long countTo(String to) {
        return sent.stream().filter(m -> List.of(m.getTo()).contains(to)).count();
    }

    /** {@code to} 宛ての最後のメールに含まれるリンクのトークン。 */
    public String tokenSentTo(String to) {
        var matcher = LINK.matcher(lastTo(to).getText());
        if (!matcher.find()) {
            throw new AssertionError("No link in the mail to " + to);
        }
        return matcher.group(1);
    }
}
