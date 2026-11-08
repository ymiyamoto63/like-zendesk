package dev.likezendesk.devtools.app;

import static dev.likezendesk.devtools.app.DemoData.Flow.ASKED;
import static dev.likezendesk.devtools.app.DemoData.Flow.ASSIGNED;
import static dev.likezendesk.devtools.app.DemoData.Flow.NEW;
import static dev.likezendesk.devtools.app.DemoData.Flow.REPLIED;
import static dev.likezendesk.devtools.app.DemoData.Flow.SOLVED;
import static dev.likezendesk.ticket.domain.Priority.HIGH;
import static dev.likezendesk.ticket.domain.Priority.LOW;
import static dev.likezendesk.ticket.domain.Priority.NORMAL;
import static dev.likezendesk.ticket.domain.Priority.URGENT;
import static dev.likezendesk.ticket.domain.TicketType.INCIDENT;
import static dev.likezendesk.ticket.domain.TicketType.PROBLEM;
import static dev.likezendesk.ticket.domain.TicketType.QUESTION;
import static dev.likezendesk.ticket.domain.TicketType.TASK;

import dev.likezendesk.devtools.app.DemoData.Person;
import dev.likezendesk.devtools.app.DemoData.Plan;
import dev.likezendesk.devtools.app.DemoData.Story;
import dev.likezendesk.tenant.domain.RequesterSignup;
import java.util.List;

/** デモテナントの中身。人名・会社名・メールアドレスはすべて架空（example ドメイン）。 */
final class DemoStories {

    private DemoStories() {}

    /** 社外の顧客を支えるサポート窓口。依頼者は誰でも登録できる。 */
    static Plan support() {
        return new Plan(
                "demo-support",
                "ミドリクラウド サポート",
                RequesterSignup.OPEN,
                List.of(),
                new Person("佐藤 一郎", "admin@support.example.com", null),
                List.of(
                        new Person("鈴木 花子", "suzuki@support.example.com", null),
                        new Person("高橋 健", "takahashi@support.example.com", null)),
                List.of("一次対応", "技術サポート"),
                List.of("株式会社サンプル商事", "ひかり製作所"),
                List.of(
                        new Person("山田 太郎", "yamada@sample-shoji.example.jp", "株式会社サンプル商事"),
                        new Person("中村 美咲", "nakamura@sample-shoji.example.jp", "株式会社サンプル商事"),
                        new Person("小林 誠", "kobayashi@hikari-mfg.example.jp", "ひかり製作所"),
                        new Person("加藤 由美", "kato@example.net", null)),
                List.of(
                        new Story(
                                "請求書の宛名を変更したい",
                                "来月分から請求書の宛名を「株式会社サンプル商事 経理部」に変更できますか。",
                                0,
                                LOW,
                                QUESTION,
                                List.of("請求"),
                                0,
                                1,
                                SOLVED,
                                "変更後の宛名と、適用を始めたい月を教えてください。",
                                "宛名は「株式会社サンプル商事 経理部」、11 月分からでお願いします。",
                                ""),
                        new Story(
                                "管理画面にログインできない",
                                "今朝からパスワードを入れても「認証に失敗しました」と表示されます。",
                                1,
                                HIGH,
                                INCIDENT,
                                List.of("ログイン"),
                                0,
                                1,
                                SOLVED,
                                "ブラウザの種類と、エラーが表示された時刻を教えてください。",
                                "Chrome で、9 時 10 分ごろです。",
                                "同時刻に認証基盤の証明書更新あり。影響範囲を確認する。"),
                        new Story(
                                "API のレスポンスが遅い",
                                "昨日の夕方から、注文 API の応答に 5 秒以上かかることがあります。",
                                2,
                                URGENT,
                                INCIDENT,
                                List.of("api", "性能"),
                                1,
                                2,
                                REPLIED,
                                "遅くなるリクエストの例（時刻とリクエスト ID）を 2〜3 件いただけますか。",
                                "18:02 の req-7f3a と 18:15 の req-81c0 です。",
                                "DB のスロークエリログを確認中。"),
                        new Story(
                                "CSV エクスポートの文字化け",
                                "注文一覧を CSV で出力すると、Excel で開いたときに日本語が文字化けします。",
                                0,
                                NORMAL,
                                PROBLEM,
                                List.of("csv"),
                                1,
                                2,
                                ASKED,
                                "お使いの Excel のバージョンを教えてください。BOM 付き UTF-8 での出力も試せます。",
                                "",
                                ""),
                        new Story(
                                "ユーザーを 10 人追加したい",
                                "新しい部署ができたので、アカウントを 10 人分追加したいです。手順を教えてください。",
                                1,
                                NORMAL,
                                TASK,
                                List.of("アカウント"),
                                0,
                                1,
                                SOLVED,
                                "追加する方のメールアドレスの一覧を送っていただければ、こちらで招待します。",
                                "一覧を添付の代わりに本文に書きます。a@sample-shoji.example.jp ほか 9 名です。",
                                ""),
                        new Story(
                                "Webhook が届かない",
                                "注文確定時の Webhook が、こちらのサーバーに届いていないようです。",
                                2,
                                HIGH,
                                INCIDENT,
                                List.of("api", "webhook"),
                                1,
                                2,
                                ASSIGNED,
                                "",
                                "",
                                "送信ログを確認する。相手先の TLS 設定の可能性あり。"),
                        new Story(
                                "二段階認証を有効にしたい",
                                "管理者アカウントで二段階認証を使いたいのですが、設定画面が見つかりません。",
                                3,
                                NORMAL,
                                QUESTION,
                                List.of("ログイン"),
                                0,
                                1,
                                ASKED,
                                "お使いのアカウントの権限（管理者かどうか）を確認させてください。",
                                "",
                                ""),
                        new Story(
                                "検索結果に古い注文が出ない",
                                "注文検索で、1 年より前の注文が見つかりません。仕様でしょうか。",
                                0,
                                NORMAL,
                                QUESTION,
                                List.of(),
                                0,
                                1,
                                NEW,
                                "",
                                "",
                                ""),
                        new Story(
                                "画面が真っ白になる",
                                "ダッシュボードを開くと、画面が真っ白のまま何も表示されません。",
                                2,
                                HIGH,
                                INCIDENT,
                                List.of(),
                                0,
                                1,
                                NEW,
                                "",
                                "",
                                ""),
                        new Story(
                                "契約プランの違いを知りたい",
                                "スタンダードとプロの違い（API の呼び出し上限など）を教えてください。",
                                3,
                                LOW,
                                QUESTION,
                                List.of("契約"),
                                0,
                                1,
                                SOLVED,
                                "主にどの機能をお使いの予定か教えていただけますか。",
                                "API 連携が中心です。",
                                "")));
    }

    /** 社員からの依頼を受ける社内 IT ヘルプデスク。依頼者は会社のドメインのアドレスだけ登録できる。 */
    static Plan helpdesk() {
        return new Plan(
                "demo-helpdesk",
                "情報システム部 ヘルプデスク",
                RequesterSignup.DOMAIN_RESTRICTED,
                List.of("corp.example.co.jp"),
                new Person("伊藤 直樹", "ito@corp.example.co.jp", null),
                List.of(
                        new Person("渡辺 さくら", "watanabe@corp.example.co.jp", null),
                        new Person("山本 翔", "yamamoto@corp.example.co.jp", null)),
                List.of("インフラ", "アカウント管理"),
                List.of("営業部", "経理部", "開発部"),
                List.of(
                        new Person("田中 一樹", "tanaka@corp.example.co.jp", "営業部"),
                        new Person("吉田 葵", "yoshida@corp.example.co.jp", "経理部"),
                        new Person("松本 大輔", "matsumoto@corp.example.co.jp", "開発部"),
                        new Person("井上 舞", "inoue@corp.example.co.jp", "営業部")),
                List.of(
                        new Story(
                                "VPN に接続できない",
                                "自宅から VPN に接続しようとすると「タイムアウトしました」と表示されます。",
                                0,
                                HIGH,
                                INCIDENT,
                                List.of("vpn"),
                                0,
                                1,
                                SOLVED,
                                "VPN クライアントのバージョンと、接続しようとした時刻を教えてください。",
                                "バージョン 5.2.1 で、8 時 50 分ごろです。",
                                "同時間帯に VPN 装置の CPU 使用率が高い。ベンダーに確認中。"),
                        new Story(
                                "パスワードを忘れた",
                                "社内ポータルのパスワードを忘れてしまいました。リセットをお願いします。",
                                1,
                                NORMAL,
                                TASK,
                                List.of("アカウント"),
                                1,
                                2,
                                SOLVED,
                                "本人確認のため、社員番号を教えてください。",
                                "社員番号は 10234 です。",
                                ""),
                        new Story(
                                "プリンターで印刷できない",
                                "3 階の複合機で印刷すると、ジョブが消えてしまいます。",
                                3,
                                NORMAL,
                                INCIDENT,
                                List.of("プリンター"),
                                0,
                                1,
                                REPLIED,
                                "お使いの PC の名前（資産番号）を教えてください。",
                                "PC の資産番号は PC-0312 です。",
                                "ドライバーの配布ポリシーが古い可能性。"),
                        new Story(
                                "新しいノート PC の手配",
                                "来月入社する方のノート PC を 1 台手配してください。開発用で、メモリ 32GB 希望です。",
                                2,
                                LOW,
                                TASK,
                                List.of("pc", "手配"),
                                0,
                                1,
                                ASKED,
                                "入社日と、受け取り場所を教えてください。",
                                "",
                                ""),
                        new Story(
                                "メールが送信できない",
                                "Outlook でメールを送ると、送信トレイに残ったままになります。",
                                0,
                                HIGH,
                                INCIDENT,
                                List.of("メール"),
                                0,
                                1,
                                ASSIGNED,
                                "",
                                "",
                                "メールサーバー側の送信制限に引っかかっていないか確認する。"),
                        new Story(
                                "Wi-Fi が頻繁に切れる",
                                "会議室 B で Wi-Fi が数分おきに切れます。",
                                3,
                                NORMAL,
                                PROBLEM,
                                List.of("ネットワーク"),
                                0,
                                1,
                                ASKED,
                                "切れたときに、他の方の端末も同時に切れていたか分かりますか。",
                                "",
                                ""),
                        new Story(
                                "ソフトウェアのインストール許可",
                                "業務で画像編集ソフトを使いたいので、インストールを許可してください。",
                                2,
                                LOW,
                                QUESTION,
                                List.of("ソフトウェア"),
                                1,
                                2,
                                NEW,
                                "",
                                "",
                                ""),
                        new Story(
                                "VPN が遅い",
                                "VPN 接続中、社内のファイルサーバーへのアクセスが非常に遅いです。",
                                0,
                                NORMAL,
                                PROBLEM,
                                List.of("vpn"),
                                0,
                                1,
                                NEW,
                                "",
                                "",
                                ""),
                        new Story(
                                "共有フォルダの権限を追加したい",
                                "経理部の共有フォルダに、新しく異動した 2 名の書き込み権限を付けてください。",
                                1,
                                NORMAL,
                                TASK,
                                List.of("アカウント", "権限"),
                                1,
                                2,
                                SOLVED,
                                "上長の承認を得ているか確認させてください。",
                                "部長の承認済みです。",
                                "")));
    }
}
