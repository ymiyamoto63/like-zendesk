package dev.likezendesk.devtools.app;

import dev.likezendesk.devtools.domain.AdjustableClock;
import dev.likezendesk.identity.app.Groups;
import dev.likezendesk.identity.app.Organizations;
import dev.likezendesk.identity.app.Provisioning;
import dev.likezendesk.identity.app.Signup;
import dev.likezendesk.identity.domain.Role;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import dev.likezendesk.tenant.domain.RequesterSignup;
import dev.likezendesk.ticket.app.AutoClose;
import dev.likezendesk.ticket.app.Requests;
import dev.likezendesk.ticket.app.Tickets;
import dev.likezendesk.ticket.domain.Patch;
import dev.likezendesk.ticket.domain.Priority;
import dev.likezendesk.ticket.domain.TicketStatus;
import dev.likezendesk.ticket.domain.TicketType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * デモ用の 2 テナント（F-D-1）: 社外サポート型の {@code demo-support} と、社内ヘルプデスク型の {@code demo-helpdesk}。
 *
 * <p>画面の操作と同じサービスを通して作るので、監査ログと通知メールも残る。チケットは過去 3 週間に散らばるよう、時計を戻しながら作る。
 */
@Service
@Profile({"dev", "e2e"})
public class DemoData {

    /** README に載せるデモアカウントのパスワード。 */
    public static final String PASSWORD = "demo-password";

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final Signup signup;
    private final Tenants tenants;
    private final Provisioning provisioning;
    private final Groups groups;
    private final Organizations organizations;
    private final Tickets tickets;
    private final Requests requests;
    private final AutoClose autoClose;
    private final AdjustableClock clock;

    DemoData(
            Signup signup,
            Tenants tenants,
            Provisioning provisioning,
            Groups groups,
            Organizations organizations,
            Tickets tickets,
            Requests requests,
            AutoClose autoClose,
            AdjustableClock clock) {
        this.signup = signup;
        this.tenants = tenants;
        this.provisioning = provisioning;
        this.groups = groups;
        this.organizations = organizations;
        this.tickets = tickets;
        this.requests = requests;
        this.autoClose = autoClose;
        this.clock = clock;
    }

    /** チケットの進み具合。後のものほど先まで進める。 */
    enum Flow {
        NEW,
        ASSIGNED,
        ASKED,
        REPLIED,
        SOLVED
    }

    record Person(String name, String email, @Nullable String organization) {}

    /** {@code requester} は依頼者の番号、{@code group} と {@code agent} はグループと担当者の番号。 */
    record Story(
            String subject,
            String body,
            int requester,
            Priority priority,
            TicketType type,
            List<String> tags,
            int group,
            int agent,
            Flow flow,
            String question,
            String answer,
            String note) {}

    record Plan(
            String slug,
            String name,
            RequesterSignup signup,
            List<String> domains,
            Person admin,
            List<Person> agents,
            List<String> groupNames,
            List<String> organizationNames,
            List<Person> requesters,
            List<Story> stories) {}

    /** 未投入のテナントだけを作る。作ったテナントの数を返す。 */
    public int seed() {
        int created = 0;
        for (var plan : List.of(DemoStories.support(), DemoStories.helpdesk())) {
            if (exists(plan.slug())) {
                continue;
            }
            seed(plan);
            created++;
        }
        clock.reset();
        // 4 日より前に解決したチケットをクローズする
        autoClose.closeDue();
        return created;
    }

    private boolean exists(String slug) {
        try {
            tenants.require(slug);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private void seed(Plan plan) {
        // 前のテナントで進めた時計を戻してから、3 週間前に移る
        clock.reset();
        var start = clock.instant().minus(Duration.ofDays(21));
        clock.travelTo(start);
        var admin = signup.signup(new Signup.Command(
                plan.name(), plan.slug(), plan.admin().name(), plan.admin().email(), PASSWORD));
        if (plan.signup() == RequesterSignup.DOMAIN_RESTRICTED) {
            tenants.updateSettings(admin, plan.name(), plan.signup(), plan.domains());
        }
        var hash = provisioning.hash(PASSWORD);

        var orgIds = new ArrayList<UUID>();
        for (var name : plan.organizationNames()) {
            orgIds.add(organizations.create(admin, name).id());
        }
        var agents = new ArrayList<Viewer.Staff>();
        agents.add(admin);
        for (var person : plan.agents()) {
            agents.add((Viewer.Staff) provisioning
                    .createUser(admin, person.email(), person.name(), Role.AGENT, hash, null)
                    .viewer());
        }
        var requesters = new ArrayList<Viewer.Requester>();
        for (var person : plan.requesters()) {
            var org = person.organization() == null
                    ? null
                    : orgIds.get(plan.organizationNames().indexOf(person.organization()));
            requesters.add((Viewer.Requester) provisioning
                    .createUser(admin, person.email(), person.name(), Role.REQUESTER, hash, org)
                    .viewer());
        }
        // グループ i には、管理者と、エージェント i と i+1 が所属する
        var groupIds = new ArrayList<UUID>();
        for (int i = 0; i < plan.groupNames().size(); i++) {
            var id = groups.create(admin, plan.groupNames().get(i)).id();
            var members = List.of(
                    agents.get(0).userId().value(),
                    agents.get(1 + i % plan.agents().size()).userId().value(),
                    agents.get(1 + (i + 1) % plan.agents().size()).userId().value());
            groups.replaceMembers(admin, id, members.stream().distinct().toList());
            groupIds.add(id);
        }

        var step = Duration.ofDays(20).dividedBy(plan.stories().size());
        for (int i = 0; i < plan.stories().size(); i++) {
            clock.travelTo(start.plus(Duration.ofHours(6)).plus(step.multipliedBy(i)));
            // 解決から 4 日たったものを、その時点でクローズする（実際の定期ジョブと同じ）
            autoClose.closeDue();
            play(plan.stories().get(i), requesters, agents, groupIds);
        }
        log.info(
                "Seeded demo tenant {} ({} tickets)",
                plan.slug(),
                plan.stories().size());
    }

    private void play(Story story, List<Viewer.Requester> requesters, List<Viewer.Staff> agents, List<UUID> groupIds) {
        var requester = requesters.get(story.requester());
        var number = requests.create(requester, story.subject(), story.body(), List.of())
                .number();
        if (story.flow() == Flow.NEW) {
            return;
        }
        var agent = agents.get(story.agent());
        later(Duration.ofMinutes(40));
        update(agent, number, TicketStatus.OPEN, story, groupIds);
        if (!story.note().isEmpty()) {
            later(Duration.ofMinutes(15));
            tickets.comment(agent, number, story.note(), false, List.of(), null);
        }
        if (story.flow() == Flow.ASSIGNED) {
            return;
        }
        later(Duration.ofMinutes(30));
        tickets.comment(agent, number, story.question(), true, List.of(), changes(agent, number, TicketStatus.PENDING));
        if (story.flow() == Flow.ASKED) {
            return;
        }
        later(Duration.ofHours(3));
        requests.reply(requester, number, "確認しました。" + story.answer(), List.of());
        if (story.flow() == Flow.REPLIED) {
            return;
        }
        later(Duration.ofHours(2));
        tickets.comment(
                agent,
                number,
                "ご確認ありがとうございます。こちらで対応が完了しましたので、解決済みとします。再発した場合はこのチケットに返信してください。",
                true,
                List.of(),
                changes(agent, number, TicketStatus.SOLVED));
    }

    private void update(Viewer.Staff agent, long number, TicketStatus status, Story story, List<UUID> groupIds) {
        var current = tickets.get(agent, number);
        tickets.update(
                agent,
                number,
                new Tickets.Changes(
                        current.version(),
                        null,
                        status,
                        story.priority(),
                        story.type(),
                        Patch.set(groupIds.get(story.group())),
                        Patch.set(agent.userId().value()),
                        story.tags()));
    }

    private Tickets.Changes changes(Viewer.Staff agent, long number, TicketStatus status) {
        var current = tickets.get(agent, number);
        return new Tickets.Changes(current.version(), null, status, null, null, Patch.keep(), Patch.keep(), null);
    }

    private void later(Duration amount) {
        clock.advance(amount);
    }
}
