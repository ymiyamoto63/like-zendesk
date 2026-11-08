package dev.likezendesk.identity.infra;

import dev.likezendesk.identity.domain.TokenPurpose;
import dev.likezendesk.shared.UserId;
import dev.likezendesk.shared.Viewer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 招待・メールアドレス確認・パスワード再設定のトークン。トークンのハッシュで引く。 */
@Repository
public class UserTokenRepository {

    private final JdbcClient jdbc;

    UserTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record ValidToken(UUID id, UserId userId) {}

    public void insert(Viewer.System viewer, UserId userId, TokenPurpose purpose, byte[] hash, Instant expiresAt) {
        jdbc.sql("""
                        INSERT INTO user_tokens (tenant_id, user_id, purpose, token_hash, expires_at)
                        VALUES (:v_tenant, :user, :purpose, :hash, :expires)
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("user", userId.value())
                .param("purpose", purpose.dbValue())
                .param("hash", hash)
                .param("expires", Timestamp.from(expiresAt))
                .update();
    }

    /** 未使用で期限内のトークン。 */
    public Optional<ValidToken> findValid(Viewer.System viewer, TokenPurpose purpose, byte[] hash, Instant now) {
        return jdbc.sql("""
                        SELECT id, user_id FROM user_tokens
                        WHERE tenant_id = :v_tenant AND purpose = :purpose AND token_hash = :hash
                          AND used_at IS NULL AND expires_at > :now
                        """)
                .param("v_tenant", viewer.tenantId().value())
                .param("purpose", purpose.dbValue())
                .param("hash", hash)
                .param("now", Timestamp.from(now))
                .query((rs, row) ->
                        new ValidToken(rs.getObject("id", UUID.class), new UserId(rs.getObject("user_id", UUID.class))))
                .optional();
    }

    /** 使用済みにする。同時に使われて既に使用済みなら false。 */
    public boolean markUsed(Viewer.System viewer, UUID id, Instant now) {
        return jdbc.sql("""
                                UPDATE user_tokens SET used_at = :now
                                WHERE tenant_id = :v_tenant AND id = :id AND used_at IS NULL
                                """)
                        .param("v_tenant", viewer.tenantId().value())
                        .param("id", id)
                        .param("now", Timestamp.from(now))
                        .update()
                == 1;
    }
}
