package dev.likezendesk.tenant.app;

import dev.likezendesk.shared.ApiException;
import dev.likezendesk.tenant.domain.RequesterSignup;
import dev.likezendesk.tenant.domain.Tenant;
import dev.likezendesk.tenant.domain.TenantSlugs;
import dev.likezendesk.tenant.infra.TenantDirectory;
import java.time.Clock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** テナントの解決と作成。他の機能はテナントをここから得る。 */
@Service
public class Tenants {

    private final TenantDirectory directory;
    private final Clock clock;

    Tenants(TenantDirectory directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
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
