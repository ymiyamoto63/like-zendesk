package dev.likezendesk.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlConditionTest {

    @Test
    void andJoinsBothConditionsAndMergesParameters() {
        var a = new SqlCondition("t.tenant_id = :v_tenant", Map.of("v_tenant", 1));
        var b = new SqlCondition("t.requester_id = :v_user", Map.of("v_tenant", 1, "v_user", 2));

        var both = a.and(b);

        assertThat(both.sql()).isEqualTo("(t.tenant_id = :v_tenant) AND (t.requester_id = :v_user)");
        assertThat(both.params()).isEqualTo(Map.of("v_tenant", 1, "v_user", 2));
    }

    @Test
    void andRejectsTheSameParameterWithDifferentValues() {
        var a = new SqlCondition("a.tenant_id = :v_tenant", Map.of("v_tenant", 1));
        var b = new SqlCondition("b.tenant_id = :v_tenant", Map.of("v_tenant", 2));

        assertThatIllegalArgumentException().isThrownBy(() -> a.and(b));
    }
}
