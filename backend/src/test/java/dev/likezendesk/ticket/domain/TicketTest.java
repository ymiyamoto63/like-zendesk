package dev.likezendesk.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final Actor STAFF = new Actor.Staff(new UserId(UUID.randomUUID()));
    private static final Ref GROUP = new Ref(UUID.randomUUID(), "インフラ");
    private static final Ref SATO = new Ref(UUID.randomUUID(), "佐藤");

    private static Ticket newTicket() {
        return new Ticket(
                UUID.randomUUID(),
                1,
                "VPN に接続できない",
                new Ref(UUID.randomUUID(), "山田"),
                TicketStatus.NEW,
                Priority.NORMAL,
                TicketType.QUESTION,
                null,
                null,
                List.of(),
                null,
                0,
                CREATED,
                CREATED,
                null,
                null);
    }

    private static Ticket.Update assign(Ref group, Ref assignee) {
        return new Ticket.Update(null, null, null, null, Patch.set(group), Patch.set(assignee), null);
    }

    @Test
    void assigningANewTicketOpensItAndRecordsEachChangedField() {
        var applied = newTicket().apply(assign(GROUP, SATO), STAFF, NOW);

        assertThat(applied.ticket().status()).isEqualTo(TicketStatus.OPEN);
        assertThat(applied.ticket().version()).isEqualTo(1);
        assertThat(applied.ticket().updatedAt()).isEqualTo(NOW);
        assertThat(applied.changes())
                .containsExactly(
                        new Ticket.FieldChange("status", "new", "open"),
                        new Ticket.FieldChange(
                                "group", null, Map.of("id", GROUP.id().toString(), "name", "インフラ")),
                        new Ticket.FieldChange(
                                "assignee", null, Map.of("id", SATO.id().toString(), "name", "佐藤")));
    }

    @Test
    void unchangedValuesProduceNoChanges() {
        var ticket = newTicket();
        var applied = ticket.apply(
                new Ticket.Update(
                        "VPN に接続できない",
                        TicketStatus.NEW,
                        Priority.NORMAL,
                        null,
                        Patch.keep(),
                        Patch.set(null),
                        List.of()),
                STAFF,
                NOW);
        assertThat(applied.changes()).isEmpty();
        assertThat(applied.ticket()).isSameAs(ticket);
    }

    @Test
    void solvingSetsSolvedAtAndReopeningClearsIt() {
        var open = newTicket().apply(assign(GROUP, SATO), STAFF, NOW).ticket();
        var solved = open.apply(Ticket.Update.statusOnly(TicketStatus.SOLVED), STAFF, NOW)
                .ticket();
        assertThat(solved.solvedAt()).isEqualTo(NOW);

        var reopened = solved.apply(Ticket.Update.statusOnly(TicketStatus.OPEN), STAFF, NOW.plusSeconds(60))
                .ticket();
        assertThat(reopened.solvedAt()).isNull();
    }

    @Test
    void tagsAreNormalized() {
        var applied = newTicket()
                .apply(
                        new Ticket.Update(
                                null,
                                null,
                                null,
                                null,
                                Patch.keep(),
                                Patch.keep(),
                                List.of(" VPN", "vpn", "remote ", "")),
                        STAFF,
                        NOW);
        assertThat(applied.ticket().tags()).containsExactly("remote", "vpn");
    }

    @Test
    void anAssigneeNeedsAGroup() {
        assertThatThrownBy(() -> newTicket()
                        .apply(
                                new Ticket.Update(null, null, null, null, Patch.keep(), Patch.set(SATO), null),
                                STAFF,
                                NOW))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo("assignee.requires_group"));
    }

    @Test
    void removingTheAssigneeOfASolvedTicketIsRejected() {
        var open = newTicket().apply(assign(GROUP, SATO), STAFF, NOW).ticket();
        var solved = open.apply(Ticket.Update.statusOnly(TicketStatus.SOLVED), STAFF, NOW)
                .ticket();
        assertThatThrownBy(() -> solved.apply(
                        new Ticket.Update(null, null, null, null, Patch.keep(), Patch.set(null), null), STAFF, NOW))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo("status.solved_requires_assignee"));
    }
}
