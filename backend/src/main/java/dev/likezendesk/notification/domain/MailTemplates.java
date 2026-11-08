package dev.likezendesk.notification.domain;

import java.util.Map;

/**
 * 通知の種類と値からメールを作る。本文はプレーンテキスト（docs/design/architecture.md 2.7）。
 *
 * <p>値（payload）は書き込み時に決めたもので、{@code to}、{@code tenantName}、{@code url} を持つ。チケットの通知は
 * {@code number}、{@code subject}、{@code body}（本文やコメント）、{@code author}（書いた人）も持つ。
 */
public final class MailTemplates {

    private MailTemplates() {}

    /** 例: [デモ社] #12 VPN に接続できない - 返信がありました */
    private static String ticketSubject(Map<String, String> payload, String what) {
        return "[%s] #%s %s - %s"
                .formatted(payload.get("tenantName"), payload.get("number"), payload.get("subject"), what);
    }

    public static Mail render(NotificationKind kind, Map<String, String> payload) {
        var tenant = payload.get("tenantName");
        var url = payload.get("url");
        return switch (kind) {
            case INVITATION -> new Mail(payload.get("to"), "[" + tenant + "] 招待が届いています", """
                        %s のスタッフとして招待されました。

                        次のリンクを開き、名前とパスワードを設定してください。リンクの有効期限は 7 日です。

                        %s
                        """.formatted(tenant, url));
            case EMAIL_VERIFICATION ->
                new Mail(payload.get("to"), "[" + tenant + "] メールアドレスの確認", """
                        %s のサポートポータルへの登録を受け付けました。

                        次のリンクを開き、メールアドレスを確認してください。リンクの有効期限は 24 時間です。

                        %s

                        心当たりがない場合は、このメールを破棄してください。
                        """.formatted(tenant, url));
            case PASSWORD_RESET -> new Mail(payload.get("to"), "[" + tenant + "] パスワードの再設定", """
                        パスワードの再設定を受け付けました。

                        次のリンクを開き、新しいパスワードを設定してください。リンクの有効期限は 1 時間です。

                        %s

                        心当たりがない場合は、このメールを破棄してください。パスワードは変わりません。
                        """.formatted(url));
            case TICKET_CREATED ->
                new Mail(payload.get("to"), ticketSubject(payload, "お問い合わせを受け付けました"), """
                        お問い合わせを受け付けました。担当者から返信しますので、しばらくお待ちください。

                        件名: %s

                        %s

                        状況の確認と返信はこちらから:
                        %s
                        """.formatted(
                                payload.get("subject"), payload.get("body"), url));
            case PUBLIC_COMMENT ->
                new Mail(payload.get("to"), ticketSubject(payload, "返信がありました"), """
                        %s さんが返信しました。

                        %s

                        返信はこちらから:
                        %s
                        """.formatted(
                                payload.get("author"), payload.get("body"), url));
            case ASSIGNED ->
                new Mail(payload.get("to"), ticketSubject(payload, "担当になりました"), """
                        %s さんが、次のチケットの担当者にあなたを設定しました。

                        件名: %s

                        %s
                        """.formatted(
                                payload.get("author"), payload.get("subject"), url));
        };
    }
}
