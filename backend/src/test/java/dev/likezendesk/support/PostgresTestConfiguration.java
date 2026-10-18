package dev.likezendesk.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 結合テスト用の PostgreSQL。docker compose と同じイメージ（pg_bigm、app_owner / app_runtime ロール）を使う。
 *
 * <p>API と同じく、マイグレーションは app_owner、アプリケーションの接続は app_runtime で行う。
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    static final String OWNER_PASSWORD = "owner-test";
    static final String RUNTIME_PASSWORD = "runtime-test";

    /** {@code ./mvnw verify} が結合テストの前に docker/postgres からビルドするイメージ。 */
    private static final String IMAGE = System.getProperty("postgres.test.image", "likezendesk-postgres:it");

    @Bean
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("likezendesk")
                .withEnv("APP_OWNER_PASSWORD", OWNER_PASSWORD)
                .withEnv("APP_RUNTIME_PASSWORD", RUNTIME_PASSWORD);
    }

    @Bean
    DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> "app_runtime");
            registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
            registry.add("spring.flyway.user", () -> "app_owner");
            registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
        };
    }
}
