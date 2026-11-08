package dev.likezendesk.devtools.app;

import dev.likezendesk.devtools.infra.LoadDataRepository;
import dev.likezendesk.identity.app.Groups;
import dev.likezendesk.identity.app.Provisioning;
import dev.likezendesk.identity.app.Signup;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 性能確認用のテナント {@code demo-load} に、チケット 10 万件を作る（F-D-2、N-1、N-2）。 */
@Service
@Profile({"dev", "e2e"})
public class LoadData {

    public static final String SLUG = "demo-load";
    static final int TICKETS = 100_000;
    private static final int BATCH = 10_000;

    private static final Logger log = LoggerFactory.getLogger(LoadData.class);

    private final Signup signup;
    private final Tenants tenants;
    private final Provisioning provisioning;
    private final Groups groups;
    private final LoadDataRepository repository;
    private final TransactionTemplate transaction;
    private final Clock clock;

    LoadData(
            Signup signup,
            Tenants tenants,
            Provisioning provisioning,
            Groups groups,
            LoadDataRepository repository,
            TransactionTemplate transaction,
            Clock clock) {
        this.signup = signup;
        this.tenants = tenants;
        this.provisioning = provisioning;
        this.groups = groups;
        this.repository = repository;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** すでにあれば何もしない。作ったら true。 */
    public boolean seed() {
        try {
            tenants.require(SLUG);
            return false;
        } catch (ApiException e) {
            // まだない
        }
        var admin =
                signup.signup(new Signup.Command("性能確認用", SLUG, "負荷 管理者", "admin@load.example.com", DemoData.PASSWORD));
        var hash = provisioning.hash(DemoData.PASSWORD);
        var agents = new ArrayList<UUID>();
        agents.add(admin.userId().value());
        for (int i = 1; i <= 20; i++) {
            agents.add(provisioning
                    .createUser(admin, "agent" + i + "@load.example.com", "エージェント " + i, Role.AGENT, hash, null)
                    .id()
                    .value());
        }
        var requesters = new ArrayList<UUID>();
        for (int i = 1; i <= 500; i++) {
            requesters.add(provisioning
                    .createUser(admin, "user" + i + "@load.example.com", "依頼者 " + i, Role.REQUESTER, hash, null)
                    .id()
                    .value());
        }
        var group = groups.create(admin, "サポート").id();
        groups.replaceMembers(admin, group, agents);

        var system = new Viewer.System(admin.tenantId());
        var now = clock.instant();
        var span = Duration.ofDays(365).dividedBy(TICKETS / BATCH);
        for (int i = 0; i < TICKETS / BATCH; i++) {
            var since = now.minus(Duration.ofDays(365)).plus(span.multipliedBy(i));
            transaction.executeWithoutResult(status ->
                    repository.insertTickets(system, requesters, group, agents, BATCH, since, since.plus(span)));
            log.info("Generated {} / {} tickets", (i + 1) * BATCH, TICKETS);
        }
        repository.analyze(system);
        return true;
    }
}
