package dev.likezendesk.identity.web;

import dev.likezendesk.identity.app.Profiles;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

record MeResponse(
        UUID id,
        String name,
        String email,

        @Schema(allowableValues = {"requester", "agent", "admin"})
        String role,

        List<Group> groups) {

    record Group(UUID id, String name) {}

    static MeResponse of(Profiles.Me me) {
        return new MeResponse(
                me.id().value(),
                me.name(),
                me.email(),
                me.role().dbValue(),
                me.groups().stream().map(g -> new Group(g.id(), g.name())).toList());
    }
}
