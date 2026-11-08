package dev.likezendesk.ticket.domain;

import dev.likezendesk.shared.UserId;

/** チケットを変更する主体。ステータス遷移の規則は実行者によって変わる。 */
public sealed interface Actor {

    record Staff(UserId id) implements Actor {}

    record Requester(UserId id) implements Actor {}

    /** 自動クローズなどの定期ジョブ。監査ログの実行者は空になる。 */
    record System() implements Actor {}
}
