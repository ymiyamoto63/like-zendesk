package dev.likezendesk.devtools.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 実際の時刻からずらせる時計。デモデータを過去の日時で作るときと、E2E で日数を進めるとき（受け入れ基準 8）に使う。dev / e2e プロファイルでだけ Bean になる。
 */
public final class AdjustableClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    public AdjustableClock(Clock base) {
        this.base = base;
    }

    public void advance(Duration amount) {
        offset.updateAndGet(current -> current.plus(amount));
    }

    /** {@code at} が今になるようにずらす。 */
    public void travelTo(Instant at) {
        offset.set(Duration.between(base.instant(), at));
    }

    public void reset() {
        offset.set(Duration.ZERO);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("The application clock is always UTC");
    }
}
