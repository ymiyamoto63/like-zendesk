package dev.likezendesk.tenant.domain;

import java.util.Locale;

/** 依頼者の登録方法（F-A-2）。 */
public enum RequesterSignup {
    /** 誰でも自己登録できる（社外顧客サポート向け）。 */
    OPEN,
    /** 許可したメールドメインのアドレスだけ自己登録できる（社内ヘルプデスク向け）。 */
    DOMAIN_RESTRICTED;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RequesterSignup fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
