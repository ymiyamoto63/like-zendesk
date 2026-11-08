package dev.likezendesk.notification.domain;

import java.util.Map;

/**
 * 通知の種類と値からメールを作る。本文はプレーンテキスト（docs/design/architecture.md 2.7）。
 *
 * <p>値（payload）は書き込み時に決めたもので、{@code to}、{@code tenantName}、{@code url} を持つ。
 */
public final class MailTemplates {

    private MailTemplates() {}

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
        };
    }
}
