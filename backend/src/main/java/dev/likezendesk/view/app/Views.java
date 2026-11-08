package dev.likezendesk.view.app;

import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.ticket.app.Tickets;
import dev.likezendesk.ticket.domain.TicketFilter;
import dev.likezendesk.ticket.domain.TicketPage;
import dev.likezendesk.view.domain.View;
import dev.likezendesk.view.infra.ViewRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ビューの一覧・作成・変更と、ビューによるチケット一覧（F-V-1〜5）。 */
@Service
public class Views {

    private final ViewRepository views;
    private final Tickets tickets;

    Views(ViewRepository views, Tickets tickets) {
        this.views = views;
        this.tickets = tickets;
    }

    public record Counted(View view, @Nullable Long count) {}

    /** 標準・共有・個人の順。{@code withCounts} なら各ビューのチケット数も数える。 */
    @Transactional(readOnly = true)
    public List<Counted> list(Viewer.Staff viewer, boolean withCounts) {
        var all = new ArrayList<View>(View.BUILTINS);
        all.addAll(views.list(viewer));
        return all.stream()
                .map(v -> new Counted(v, withCounts ? countOrNull(viewer, v) : null))
                .toList();
    }

    /** ビューの条件で、全文検索も加えてチケットを探す。{@code sort} が空でなければビューの並び順より優先する。 */
    @Transactional(readOnly = true)
    public TicketPage tickets(
            Viewer.Staff viewer, String viewId, String query, List<TicketFilter.Sort> sort, int page, int size) {
        var filter = find(viewer, viewId).filter();
        if (!sort.isEmpty()) {
            filter = filter.withSort(sort);
        }
        return tickets.search(viewer, filter.validated(), query, page, size);
    }

    @Transactional
    public View create(Viewer.Staff viewer, String name, TicketFilter filter, boolean shared) {
        if (shared && !(viewer instanceof Viewer.Admin)) {
            throw ApiException.notFound();
        }
        var validated = filter.validated();
        var id = views.insert(viewer, shared ? null : viewer.userId().value(), name.strip(), validated);
        return find(viewer, id.toString());
    }

    @Transactional
    public View update(Viewer.Staff viewer, String id, String name, TicketFilter filter) {
        var view = editable(viewer, id);
        views.update(viewer, UUID.fromString(view.id()), name.strip(), filter.validated());
        return find(viewer, id);
    }

    @Transactional
    public void delete(Viewer.Staff viewer, String id) {
        var view = editable(viewer, id);
        views.delete(viewer, UUID.fromString(view.id()));
    }

    /** 標準ビュー、または閲覧者に見える共有・個人ビュー。見つからなければ 404。 */
    public View find(Viewer.Staff viewer, String id) {
        if (id.startsWith(View.BUILTIN_PREFIX)) {
            return View.BUILTINS.stream()
                    .filter(v -> v.id().equals(id))
                    .findFirst()
                    .orElseThrow(ApiException::notFound);
        }
        try {
            return views.find(viewer, UUID.fromString(id)).orElseThrow(ApiException::notFound);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound();
        }
    }

    /** 個人ビューは本人が、共有ビューは管理者が変更できる。標準ビューは変更できない。 */
    private View editable(Viewer.Staff viewer, String id) {
        var view = find(viewer, id);
        var allowed = switch (view.kind()) {
            case BUILTIN -> false;
            case SHARED -> viewer instanceof Viewer.Admin;
            case PERSONAL -> viewer.userId().value().equals(view.ownerId());
        };
        if (!allowed) {
            throw ApiException.notFound();
        }
        return view;
    }

    /** 保存後に条件の値（グループなど）が消えても、一覧の表示は止めない。 */
    private @Nullable Long countOrNull(Viewer.Staff viewer, View view) {
        try {
            return tickets.count(viewer, view.filter().validated());
        } catch (ApiException e) {
            return null;
        }
    }
}
