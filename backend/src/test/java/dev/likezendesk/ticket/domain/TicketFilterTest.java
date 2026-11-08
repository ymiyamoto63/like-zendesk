package dev.likezendesk.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.likezendesk.shared.ApiException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TicketFilterTest {

    private static TicketFilter.Condition validate(String field, String op, @Nullable Object value) {
        return TicketFilter.of(List.of(new TicketFilter.Condition(field, op, value)))
                .validated()
                .all()
                .getFirst();
    }

    private static void assertInvalid(String field, String op, @Nullable Object value) {
        assertThatThrownBy(() -> validate(field, op, value))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo("view.invalid_condition");
    }

    @Test
    void normalizesValues() {
        assertThat(validate("status", "in", List.of("open", "open", "pending")).value())
                .isEqualTo(List.of("open", "pending"));
        assertThat(validate("tags", "has_any", List.of(" VPN ", "vpn", "Mail")).value())
                .isEqualTo(List.of("mail", "vpn"));
        assertThat(validate("assignee", "eq", "@me").value()).isEqualTo("@me");
        assertThat(validate("group", "in", List.of("@my_groups")).value()).isEqualTo(List.of("@my_groups"));
        assertThat(validate("assignee", "is_null", "ignored").value()).isNull();
        assertThat(validate("updated_at", "within_days", 7).value()).isEqualTo(7);
        var id = UUID.randomUUID().toString();
        assertThat(validate("assignee", "eq", id.toUpperCase()).value()).isEqualTo(id);
    }

    @ParameterizedTest
    @CsvSource({"subject,in", "status,eq", "assignee,in", "tags,in", "created_at,eq", "group,has_any"})
    void rejectsUnknownFieldsAndOperators(String field, String op) {
        assertInvalid(field, op, List.of("x"));
    }

    @Test
    void rejectsBadValues() {
        assertInvalid("status", "in", List.of("deleted"));
        assertInvalid("status", "in", List.of());
        assertInvalid("status", "in", "open");
        assertInvalid("status", "in", List.of(1));
        assertInvalid("assignee", "eq", "someone");
        assertInvalid("group", "in", List.of("@me"));
        assertInvalid("organization", "in", List.of("@my_groups"));
        assertInvalid("organization", "in", List.of(1));
        assertInvalid("organization", "eq", "00000000-0000-0000-0000-000000000000");
        assertInvalid("tags", "has_all", List.of("  "));
        assertInvalid("updated_at", "within_days", 0);
        assertInvalid("updated_at", "within_days", 1.5);
        assertInvalid("updated_at", "within_days", "7");
    }

    @Test
    void rejectsUnknownSortFields() {
        var filter = new TicketFilter(List.of(), List.of(new TicketFilter.Sort("requester", false)));
        assertThatThrownBy(filter::validated)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo("view.invalid_sort");
    }

    @Test
    void defaultsToRecentlyUpdatedFirst() {
        assertThat(TicketFilter.of(List.of()).sort()).isEqualTo(TicketFilter.DEFAULT_SORT);
    }

    @Test
    void parsesConditionsIntoTypedCriteria() {
        var org = UUID.randomUUID();
        var group = UUID.randomUUID();
        var filter = TicketFilter.of(List.of(
                new TicketFilter.Condition("status", "not_in", List.of("solved", "closed")),
                new TicketFilter.Condition("assignee", "eq", "@me"),
                new TicketFilter.Condition("group", "in", List.of("@my_groups", group.toString())),
                new TicketFilter.Condition("organization", "is_not_null", null),
                new TicketFilter.Condition("organization", "in", List.of(org.toString())),
                new TicketFilter.Condition("tags", "has_all", List.of("VPN")),
                new TicketFilter.Condition("created_at", "older_than_days", 30)));

        assertThat(filter.criteria())
                .containsExactly(
                        new Criterion.EnumIn(Criterion.EnumField.STATUS, List.of("solved", "closed"), true),
                        new Criterion.AssigneeIsMe(),
                        new Criterion.GroupIn(List.of(group), true),
                        new Criterion.OrganizationPresence(true),
                        new Criterion.OrganizationIn(List.of(org)),
                        new Criterion.Tags(List.of("vpn"), true),
                        new Criterion.Age(Criterion.TimeField.CREATED_AT, 30, false));
    }

    @Test
    void criteriaRoundTripThroughTheStoredForm() {
        var filter = TicketFilter.of(List.of(
                new TicketFilter.Condition("priority", "in", List.of("high", "urgent")),
                new TicketFilter.Condition("assignee", "is_null", null),
                new TicketFilter.Condition("updated_at", "within_days", 7)));

        var criteria = filter.criteria();
        var restored = TicketFilter.of(
                        criteria.stream().map(Criterion::toCondition).toList())
                .criteria();

        assertThat(restored).isEqualTo(criteria);
    }

    @Test
    void rejectsTooManyConditions() {
        var conditions = java.util.Collections.nCopies(
                TicketFilter.MAX_CONDITIONS + 1, new TicketFilter.Condition("assignee", "is_null", null));
        assertThatThrownBy(() -> TicketFilter.of(conditions).criteria())
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo("view.invalid_condition");
    }
}
