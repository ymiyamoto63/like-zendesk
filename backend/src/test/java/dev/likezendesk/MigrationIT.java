package dev.likezendesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.likezendesk.support.PostgresTestConfiguration;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** マイグレーション後のスキーマを、API と同じ app_runtime ロールの接続で確かめる。 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class MigrationIT {

    /** 追記のみの表（F-C-2、F-L-2）。 */
    private static final List<String> APPEND_ONLY = List.of("comments", "ticket_events");

    @Autowired
    JdbcClient jdbc;

    private record TablePrivileges(String table, boolean select, boolean insert, boolean update, boolean delete) {}

    @Test
    void runtimeRoleCanWriteEveryTableExceptAppendOnlyOnes() {
        var privileges = jdbc.sql("""
                        SELECT tablename AS table,
                               has_table_privilege(tablename, 'SELECT') AS select,
                               has_table_privilege(tablename, 'INSERT') AS insert,
                               has_table_privilege(tablename, 'UPDATE') AS update,
                               has_table_privilege(tablename, 'DELETE') AS delete
                        FROM pg_tables
                        WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                        """).query(TablePrivileges.class).list();

        assertThat(privileges).extracting(TablePrivileges::table).contains("tenants", "tickets", "comments");
        for (var p : privileges) {
            var appendOnly = APPEND_ONLY.contains(p.table());
            assertThat(p)
                    .as("privileges of app_runtime on %s", p.table())
                    .isEqualTo(new TablePrivileges(p.table(), true, true, !appendOnly, !appendOnly));
        }
    }

    @Test
    void runtimeRoleCannotUpdateComments() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE comments SET body = 'x'").update())
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
    }

    @Test
    void runtimeRoleCannotDeleteTicketEvents() {
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM ticket_events").update())
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
    }

    @Test
    void referencesAcrossTenantsAreRejected() {
        var tenantA = insertTenant("tenant-a");
        var tenantB = insertTenant("tenant-b");
        var orgOfB = jdbc.sql("INSERT INTO organizations (tenant_id, name) VALUES (:t, 'B 社') RETURNING id")
                .param("t", tenantB)
                .query(UUID.class)
                .single();

        assertThatThrownBy(() ->
                        jdbc.sql("""
                                INSERT INTO users (tenant_id, email, name, role, organization_id, created_at)
                                VALUES (:t, 'a@example.com', 'A', 'requester', :org, now())
                                """).param("t", tenantA).param("org", orgOfB).update())
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23503"));
    }

    @Test
    void bigramIndexIsUsableForJapaneseSubjects() {
        jdbc.sql("SET LOCAL enable_seqscan = off").update();
        var plan = jdbc.sql("EXPLAIN SELECT 1 FROM tickets WHERE subject LIKE '%接続%'")
                .query(String.class)
                .list();

        assertThat(String.join("\n", plan)).contains("tickets_subject_bigm");
    }

    private UUID insertTenant(String slug) {
        return jdbc.sql("""
                        INSERT INTO tenants (slug, name, requester_signup, created_at)
                        VALUES (:slug, :slug, 'open', now()) RETURNING id
                        """).param("slug", slug).query(UUID.class).single();
    }
}
