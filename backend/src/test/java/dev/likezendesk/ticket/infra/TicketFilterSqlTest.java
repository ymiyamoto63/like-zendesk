package dev.likezendesk.ticket.infra;

import static dev.likezendesk.ticket.infra.Viewers.AGENT;
import static dev.likezendesk.ticket.infra.Viewers.USER;
import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.ticket.domain.TicketFilter;
import dev.likezendesk.ticket.domain.TicketFilter.Condition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketFilterSqlTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");

    private static dev.likezendesk.shared.SqlCondition where(Condition... conditions) {
        return TicketFilterSql.where(AGENT, TicketFilter.of(List.of(conditions)), NOW);
    }

    @Test
    void emptyFilterMatchesEverythingInTheTenant() {
        var sql = where();
        assertThat(sql.sql()).isEqualTo("TRUE");
        assertThat(sql.params()).containsOnlyKeys("v_tenant");
    }

    @Test
    void negatedEnumListsUseNotAny() {
        var sql = where(new Condition("status", "not_in", List.of("solved", "closed")));
        assertThat(sql.sql()).isEqualTo("NOT (t.status = ANY (:f0))");
        assertThat((String[]) sql.params().get("f0")).containsExactly("solved", "closed");
    }

    @Test
    void assigneeMeBindsTheViewer() {
        var sql = where(new Condition("assignee", "eq", TicketFilter.ME));
        assertThat(sql.sql()).isEqualTo("t.assignee_id = :f0");
        assertThat(sql.params()).containsEntry("f0", USER);
    }

    @Test
    void myGroupsAddsAMembershipSubselectAndBindsTheViewer() {
        var group = UUID.randomUUID();
        var sql = where(new Condition("group", "in", List.of(TicketFilter.MY_GROUPS, group.toString())));
        assertThat(sql.sql()).startsWith("(t.group_id = ANY (:f0) OR t.group_id IN (SELECT m.group_id");
        assertThat(sql.params()).containsEntry("v_user", USER);
        assertThat((UUID[]) sql.params().get("f0")).containsExactly(group);
    }

    @Test
    void tagsPickContainsOrOverlap() {
        assertThat(where(new Condition("tags", "has_all", List.of("vpn"))).sql())
                .isEqualTo("t.tags @> CAST(:f0 AS text[])");
        assertThat(where(new Condition("tags", "has_any", List.of("vpn"))).sql())
                .isEqualTo("t.tags && CAST(:f0 AS text[])");
    }

    @Test
    void ageComparesAgainstTheClock() {
        var within = where(new Condition("updated_at", "within_days", 7));
        assertThat(within.sql()).isEqualTo("t.updated_at >= :f0");
        assertThat(within.params().get("f0"))
                .isEqualTo(java.sql.Timestamp.from(NOW.minus(java.time.Duration.ofDays(7))));
        assertThat(where(new Condition("created_at", "older_than_days", 30)).sql())
                .isEqualTo("t.created_at < :f0");
    }

    @Test
    void conditionsAreJoinedWithAnd() {
        var sql = where(new Condition("assignee", "is_null", null), new Condition("organization", "is_not_null", null));
        assertThat(sql.sql())
                .startsWith("t.assignee_id IS NULL AND (SELECT ru.organization_id")
                .endsWith("IS NOT NULL");
    }
}
