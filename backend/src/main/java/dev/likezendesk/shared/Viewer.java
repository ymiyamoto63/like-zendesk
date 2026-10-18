package dev.likezendesk.shared;

import java.util.Objects;

/**
 * データを読み書きする主体。サービスとリポジトリのメソッドは、扱える閲覧者の型を第 1 引数に取る。
 *
 * <p>ロールごとに型を分けるのは、依頼者に許されない操作を呼ぶコードをコンパイルエラーにし、可視性条件の {@code switch}
 * で書き漏れを検出するため。
 */
public sealed interface Viewer permits Viewer.Requester, Viewer.Staff, Viewer.System {
    TenantId tenantId();

    /** 依頼者。自分のチケットの公開情報だけを扱える。 */
    record Requester(TenantId tenantId, UserId userId) implements Viewer {
        public Requester {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(userId, "userId");
        }
    }

    /** エージェントまたは管理者。 */
    sealed interface Staff extends Viewer permits Agent, Admin {
        UserId userId();
    }

    record Agent(TenantId tenantId, UserId userId) implements Staff {
        public Agent {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(userId, "userId");
        }
    }

    record Admin(TenantId tenantId, UserId userId) implements Staff {
        public Admin {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(userId, "userId");
        }
    }

    /** 定期ジョブ。テナント内の全データを扱える。監査ログの実行者は「システム」になる。 */
    record System(TenantId tenantId) implements Viewer {
        public System {
            Objects.requireNonNull(tenantId, "tenantId");
        }
    }
}
