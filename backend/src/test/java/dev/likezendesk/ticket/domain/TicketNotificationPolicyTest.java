package dev.likezendesk.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.notification.domain.NotificationKind;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.ticket.domain.TicketNotificationPolicy.Intent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketNotificationPolicyTest {

    private static final Instant AT = Instant.parse("2026-11-08T00:00:00Z");
    private static final Ref REQUESTER = new Ref(UUID.randomUUID(), "山田");
    private static final Ref SATO = new Ref(UUID.randomUUID(), "佐藤");
    private static final Ref SUZUKI = new Ref(UUID.randomUUID(), "鈴木");
    private static final UserId BY_REQUESTER = new UserId(REQUESTER.id());
    private static final UserId BY_SATO = new UserId(SATO.id());

    private static Ticket ticket(Ref assignee) {
        return new Ticket(
                UUID.randomUUID(),
                12,
                "VPN に接続できない",
                REQUESTER,
                TicketStatus.OPEN,
                Priority.NORMAL,
                TicketType.QUESTION,
                null,
                assignee,
                List.of(),
                null,
                0,
                AT,
                AT,
                null,
                null);
    }

    @Test
    void creatingAcknowledgesTheRequesterEvenWhenTheyCreatedItThemselves() {
        assertThat(TicketNotificationPolicy.created(ticket(null), "困っています", BY_REQUESTER))
                .containsExactly(new Intent(NotificationKind.TICKET_CREATED, REQUESTER.id(), "困っています"));
    }

    @Test
    void creatingWithAnAssigneeAlsoTellsThemUnlessTheyAreTheAuthor() {
        assertThat(TicketNotificationPolicy.created(ticket(SUZUKI), "本文", BY_SATO))
                .containsExactly(
                        new Intent(NotificationKind.TICKET_CREATED, REQUESTER.id(), "本文"),
                        new Intent(NotificationKind.ASSIGNED, SUZUKI.id(), ""));
        assertThat(TicketNotificationPolicy.created(ticket(SATO), "本文", BY_SATO))
                .extracting(Intent::kind)
                .containsExactly(NotificationKind.TICKET_CREATED);
    }

    @Test
    void publicCommentsGoToTheOtherSide() {
        assertThat(TicketNotificationPolicy.commented(ticket(SATO), BY_SATO, "ログをください", true))
                .containsExactly(new Intent(NotificationKind.PUBLIC_COMMENT, REQUESTER.id(), "ログをください"));
        assertThat(TicketNotificationPolicy.commented(ticket(SATO), BY_REQUESTER, "送りました", true))
                .containsExactly(new Intent(NotificationKind.PUBLIC_COMMENT, SATO.id(), "送りました"));
    }

    @Test
    void nobodyIsToldAboutInternalNotesOrACommentWithNoAssignee() {
        assertThat(TicketNotificationPolicy.commented(ticket(SATO), BY_SATO, "社内メモ", false))
                .isEmpty();
        assertThat(TicketNotificationPolicy.commented(ticket(null), BY_REQUESTER, "返信", true))
                .isEmpty();
    }

    @Test
    void onlyAChangedAssigneeIsToldAndNeverTheAuthor() {
        assertThat(TicketNotificationPolicy.assigned(ticket(SATO), ticket(SUZUKI), BY_SATO))
                .containsExactly(new Intent(NotificationKind.ASSIGNED, SUZUKI.id(), ""));
        assertThat(TicketNotificationPolicy.assigned(ticket(SUZUKI), ticket(SUZUKI), BY_SATO))
                .isEmpty();
        assertThat(TicketNotificationPolicy.assigned(ticket(SUZUKI), ticket(null), BY_SATO))
                .isEmpty();
        assertThat(TicketNotificationPolicy.assigned(ticket(SUZUKI), ticket(SATO), BY_SATO))
                .isEmpty();
    }

    @Test
    void solvingTellsTheAssigneeUnlessThereIsNoneOrTheyDidIt() {
        assertThat(TicketNotificationPolicy.solved(ticket(SATO), BY_REQUESTER))
                .containsExactly(new Intent(NotificationKind.SOLVED, SATO.id(), ""));
        assertThat(TicketNotificationPolicy.solved(ticket(null), BY_REQUESTER)).isEmpty();
        assertThat(TicketNotificationPolicy.solved(ticket(SATO), BY_SATO)).isEmpty();
    }
}
