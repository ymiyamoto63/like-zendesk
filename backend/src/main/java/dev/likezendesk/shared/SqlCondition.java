package dev.likezendesk.shared;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * WHERE 句に結合する SQL の断片と、その名前付きパラメーター。
 *
 * <p>可視性条件のパラメーター名は {@code v_} で始め、呼び出し側のパラメーターと衝突させない。
 */
public record SqlCondition(String sql, Map<String, Object> params) {
    public SqlCondition {
        Objects.requireNonNull(sql, "sql");
        params = Map.copyOf(params);
    }

    /** 2 つの条件の論理積。同じ名前のパラメーターは同じ値でなければならない。 */
    public SqlCondition and(SqlCondition other) {
        var merged = new HashMap<>(params);
        other.params.forEach((name, value) -> {
            var existing = merged.putIfAbsent(name, value);
            if (existing != null && !existing.equals(value)) {
                throw new IllegalArgumentException("conflicting values for parameter :" + name);
            }
        });
        return new SqlCondition("(" + sql + ") AND (" + other.sql + ")", merged);
    }
}
