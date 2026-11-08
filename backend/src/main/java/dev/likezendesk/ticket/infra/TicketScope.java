package dev.likezendesk.ticket.infra;

import dev.likezendesk.shared.SqlCondition;
import dev.likezendesk.shared.Viewer;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 閲覧者に見えるチケットだけを対象にした SQL 文を組む。チケット表（別名 {@code t}）を読み書きするリポジトリのメソッドは、ここを通す。
 *
 * <p>{@code head} の後ろに {@code WHERE <閲覧者の可視性条件> [AND <追加の条件>]} と {@code tail} を付け、可視性のパラメーターを束縛して返す。
 * 可視性を付け忘れた文は組めないので、各メソッドが {@link TicketVisibility} と {@code v_tenant} の束縛を覚えておく必要はない。
 */
final class TicketScope {

    private TicketScope() {}

    /** 例: {@code statement(jdbc, viewer, SELECT, " AND t.number = :number")}。{@code tail} は {@code AND ...}、{@code ORDER BY ...} など。 */
    static JdbcClient.StatementSpec statement(JdbcClient jdbc, Viewer viewer, String head, String tail) {
        return statement(jdbc, TicketVisibility.of(viewer, "t"), head, tail);
    }

    /** 可視性に加えて {@code extra} も満たす行だけを対象にする。 */
    static JdbcClient.StatementSpec statement(
            JdbcClient jdbc, Viewer viewer, SqlCondition extra, String head, String tail) {
        return statement(jdbc, TicketVisibility.of(viewer, "t").and(extra), head, tail);
    }

    private static JdbcClient.StatementSpec statement(JdbcClient jdbc, SqlCondition where, String head, String tail) {
        return jdbc.sql(head + " WHERE " + where.sql() + tail).params(where.params());
    }
}
