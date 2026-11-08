package dev.likezendesk.tenant.app;

import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.TenantId;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.domain.RequesterSignup;
import dev.likezendesk.tenant.domain.Tenant;
import dev.likezendesk.tenant.domain.TenantSlugs;
import dev.likezendesk.tenant.infra.TenantDirectory;
import dev.likezendesk.tenant.infra.TenantSettingsRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** テナントの解決と作成。他の機能はテナントをここから得る。 */
@Service
public class Tenants {

    private final TenantDirectory directory;
    private final TenantSettingsRepository settings;
    private final Clock clock;

    Tenants(TenantDirectory directory, TenantSettingsRepository settings, Clock clock) {
        this.directory = directory;
        this.settings = settings;
        this.clock = clock;
    }

    /** 閲覧者のテナント（名前と slug）。 */
    @Transactional(readOnly = true)
    public Tenant current(Viewer viewer) {
        return directory.find(viewer);
    }

    /** 全テナントの ID。定期ジョブ用。 */
    @Transactional(readOnly = true)
    public List<TenantId> allIds() {
        return directory.allIds();
    }

    @Transactional(readOnly = true)
    public Tenant settings(Viewer.Admin viewer) {
        return settings.get(viewer);
    }

    /** テナント名と依頼者の登録方法を変える（F-X-6、F-A-2）。ドメインは小文字にそろえ、重複を除く。 */
    @Transactional
    public Tenant updateSettings(
            Viewer.Admin viewer, String name, RequesterSignup requesterSignup, List<String> allowedDomains) {
        var domains = allowedDomains.stream()
                .map(d -> d.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (requesterSignup == RequesterSignup.DOMAIN_RESTRICTED && domains.isEmpty()) {
            throw ApiException.rule(
                    "settings.allowed_domains_required", "Domain restricted signup needs at least one domain");
        }
        return settings.update(viewer, name, requesterSignup, domains);
    }

    /** URL のテナント識別子を解決する。存在しなければ 404。 */
    @Transactional(readOnly = true)
    public Tenant require(String slug) {
        return directory.findBySlug(slug).orElseThrow(ApiException::notFound);
    }

    /** 形式は呼び出し側（web 層の入力検証と DB の CHECK 制約）で検査済みとする。呼び出し側のトランザクションの中で作る。最初の管理者の作成と同じトランザクションにするため。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public Tenant create(String slug, String name) {
        if (TenantSlugs.isReserved(slug)) {
            throw ApiException.rule("slug.reserved", "Slug is reserved");
        }
        try {
            return directory.insert(slug, name, RequesterSignup.OPEN, clock.instant());
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("slug.taken", "Slug is already taken");
        }
    }
}
