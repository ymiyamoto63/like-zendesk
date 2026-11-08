package dev.likezendesk.tenant.domain;

import java.util.Set;

/** テナント識別子（URL の /t/{slug}）の規則。 */
public final class TenantSlugs {

    /** DB の CHECK 制約と同じ。3〜32 文字の英小文字・数字・ハイフンで、両端はハイフン以外。 */
    public static final String PATTERN = "^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$";

    /** 画面や API の URL と紛らわしいため使わせない識別子。 */
    private static final Set<String> RESERVED =
            Set.of("api", "admin", "app", "assets", "help", "login", "logout", "signup", "static", "support", "www");

    private TenantSlugs() {}

    public static boolean isReserved(String slug) {
        return RESERVED.contains(slug);
    }
}
