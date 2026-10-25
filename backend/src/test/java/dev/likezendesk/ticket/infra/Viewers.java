package dev.likezendesk.ticket.infra;

import dev.likezendesk.shared.TenantId;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.util.UUID;

final class Viewers {
    static final UUID TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");
    static final UUID USER = UUID.fromString("00000000-0000-7000-8000-000000000002");

    static final Viewer.Requester REQUESTER = new Viewer.Requester(new TenantId(TENANT), new UserId(USER));
    static final Viewer.Agent AGENT = new Viewer.Agent(new TenantId(TENANT), new UserId(USER));
    static final Viewer.Admin ADMIN = new Viewer.Admin(new TenantId(TENANT), new UserId(USER));
    static final Viewer.System SYSTEM = new Viewer.System(new TenantId(TENANT));

    private Viewers() {}
}
