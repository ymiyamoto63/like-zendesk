package dev.likezendesk.identity.domain;

import java.util.List;
import java.util.UUID;

/** エージェントのグループと、所属するメンバー（F-A-3）。 */
public record Group(UUID id, String name, List<Member> members) {

    public Group {
        members = List.copyOf(members);
    }

    public record Member(UUID id, String name) {}
}
