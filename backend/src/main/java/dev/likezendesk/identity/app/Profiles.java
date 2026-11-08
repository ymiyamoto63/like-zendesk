package dev.likezendesk.identity.app;

import dev.likezendesk.identity.domain.GroupRef;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.identity.infra.UserRepository;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Profiles {

    private final UserRepository users;

    Profiles(UserRepository users) {
        this.users = users;
    }

    public record Me(UserId id, String name, String email, Role role, List<GroupRef> groups) {}

    @Transactional(readOnly = true)
    public Me me(Viewer viewer) {
        var userId = switch (viewer) {
            case Viewer.Requester r -> r.userId();
            case Viewer.Staff s -> s.userId();
            case Viewer.System _ -> throw ApiException.notFound();
        };
        var user = users.findById(viewer, userId).orElseThrow(ApiException::notFound);
        return new Me(user.id(), user.name(), user.email(), user.role(), users.groupsOf(viewer, userId));
    }
}
