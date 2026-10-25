package archfixture.ticket.infra;

import dev.likezendesk.shared.Viewer;
import org.springframework.stereotype.Repository;

@Repository
public class TicketRepository {
    public long count(Viewer viewer) {
        return 0;
    }

    /** 違反例: 閲覧者を取らない。 */
    public long countAll() {
        return 0;
    }
}
