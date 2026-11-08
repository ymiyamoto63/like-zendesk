package dev.likezendesk.identity.app;

import dev.likezendesk.identity.domain.Organization;
import dev.likezendesk.identity.infra.OrganizationRepository;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.Viewer;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 依頼者の所属組織（F-A-4）。一覧はスタッフ全員が使い（ビューの条件）、変更は管理者だけが行う。 */
@Service
public class Organizations {

    private final OrganizationRepository organizations;

    Organizations(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public List<Organization> list(Viewer.Staff viewer) {
        return organizations.list(viewer);
    }

    @Transactional
    public Organization create(Viewer.Admin viewer, String name) {
        try {
            var id = organizations.insert(viewer, name.strip());
            return organizations.find(viewer, id).orElseThrow();
        } catch (DuplicateKeyException e) {
            throw nameTaken();
        }
    }

    @Transactional
    public Organization rename(Viewer.Admin viewer, UUID id, String name) {
        try {
            if (!organizations.rename(viewer, id, name.strip())) {
                throw ApiException.notFound();
            }
        } catch (DuplicateKeyException e) {
            throw nameTaken();
        }
        return organizations.find(viewer, id).orElseThrow();
    }

    /** 所属していた依頼者は「所属なし」になる。 */
    @Transactional
    public void delete(Viewer.Admin viewer, UUID id) {
        if (!organizations.delete(viewer, id)) {
            throw ApiException.notFound();
        }
    }

    private static ApiException nameTaken() {
        return ApiException.conflict("organization.name_taken", "An organization with this name already exists");
    }
}
