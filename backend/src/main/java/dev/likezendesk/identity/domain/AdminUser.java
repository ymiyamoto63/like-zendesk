package dev.likezendesk.identity.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** 管理画面のユーザー一覧の 1 行（F-A-1、F-A-4）。 */
public record AdminUser(UserAccount account, @Nullable GroupRef organization, Instant createdAt) {

    /** 一覧の条件。null の項目では絞り込まない。{@code query} は名前かメールアドレスの部分一致。 */
    public record Query(@Nullable Role role, @Nullable Status status, String query) {}

    public enum Status {
        /** 招待を受諾していないスタッフ。 */
        INVITED,
        /** メールアドレスを確認していない依頼者。 */
        UNVERIFIED,
        ACTIVE,
        INACTIVE;

        public String dbValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public Status status() {
        if (!account.active()) {
            return Status.INACTIVE;
        }
        if (account.passwordHash() == null) {
            return Status.INVITED;
        }
        return account.emailVerified() ? Status.ACTIVE : Status.UNVERIFIED;
    }
}
