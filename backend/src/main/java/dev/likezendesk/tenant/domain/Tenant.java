package dev.likezendesk.tenant.domain;

import dev.likezendesk.shared.TenantId;
import java.util.List;

/** {@code allowedDomains} は小文字のドメイン名（例: example.com）。 */
public record Tenant(
        TenantId id, String slug, String name, RequesterSignup requesterSignup, List<String> allowedDomains) {

    public Tenant {
        allowedDomains = List.copyOf(allowedDomains);
    }

    /** このメールアドレスで依頼者が自己登録できるか（F-A-2）。{@code email} は正規化済みであること。 */
    public boolean acceptsRequesterEmail(String email) {
        return switch (requesterSignup) {
            case OPEN -> true;
            case DOMAIN_RESTRICTED -> allowedDomains.contains(email.substring(email.lastIndexOf('@') + 1));
        };
    }
}
