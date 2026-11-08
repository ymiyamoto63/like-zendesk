package dev.likezendesk.ticket.domain;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** 外すことのできる項目の変更。「変更しない」と「値を外す（null にする）」を区別する。 */
public sealed interface Patch<T> {

    record Keep<T>() implements Patch<T> {}

    record Set<T>(@Nullable T value) implements Patch<T> {}

    static <T> Patch<T> keep() {
        return new Keep<>();
    }

    static <T> Patch<T> set(@Nullable T value) {
        return new Set<>(value);
    }

    default @Nullable T applyTo(@Nullable T current) {
        return switch (this) {
            case Keep<T> _ -> current;
            case Set<T>(var value) -> value;
        };
    }

    default <R> Patch<R> map(Function<T, R> mapper) {
        return switch (this) {
            case Keep<T> _ -> new Keep<>();
            case Set<T>(var value) -> new Set<>(value == null ? null : mapper.apply(value));
        };
    }
}
