package dev.likezendesk.devtools.web;

import dev.likezendesk.devtools.app.TimeTravel;
import dev.likezendesk.shared.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** E2E テスト用の時計操作（api.md 2.6）。e2e プロファイル以外では Bean ごと存在しない。 */
@RestController
@Profile("e2e")
class E2eClockController {

    private final TimeTravel timeTravel;

    E2eClockController(TimeTravel timeTravel) {
        this.timeTravel = timeTravel;
    }

    /** {@code advance} は ISO 8601 の期間（例: {@code P4DT1H}）。 */
    record AdvanceRequest(@NotBlank String advance) {}

    record ClockResponse(Instant now, int closed, int mailed) {}

    @PostMapping("/api/_e2e/clock")
    ClockResponse advance(@Valid @RequestBody AdvanceRequest body) {
        Duration amount;
        try {
            amount = Duration.parse(body.advance());
        } catch (DateTimeParseException e) {
            throw ApiException.rule("clock.invalid_duration", "Use an ISO 8601 duration such as P4DT1H");
        }
        if (amount.isNegative()) {
            throw ApiException.rule("clock.invalid_duration", "The clock only moves forward");
        }
        var result = timeTravel.advance(amount);
        return new ClockResponse(result.now(), result.closed(), result.mailed());
    }
}
