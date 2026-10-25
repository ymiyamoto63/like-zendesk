package dev.likezendesk;

import static org.assertj.core.api.Assertions.assertThat;

import dev.likezendesk.support.ApiClient;
import dev.likezendesk.support.PostgresTestConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * フロントエンドの型の生成元（frontend/src/api/openapi.json）が、API の実装とずれていないこと。
 *
 * <p>API を変えたら {@code ./mvnw verify -Dopenapi.update=true} で書き直し、フロントエンドで {@code npm run gen:api} を実行する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class OpenApiIT {

    private static final Path COMMITTED = Path.of("../frontend/src/api/openapi.json");
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @LocalServerPort
    int port;

    @Test
    void committedSpecMatchesTheImplementation() throws IOException {
        var response = new ApiClient(port).get("/api/docs");
        assertThat(response.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = new TreeMap<>(JSON.convertValue(response.body(), Map.class));
        // 実行時のポートが入るため除く
        spec.remove("servers");
        var actual = JSON.writeValueAsString(spec) + "\n";

        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(COMMITTED, actual);
        }
        assertThat(Files.exists(COMMITTED))
                .as("%s is missing; run ./mvnw verify -Dopenapi.update=true", COMMITTED)
                .isTrue();
        assertThat(Files.readString(COMMITTED))
                .as("%s is out of date; run ./mvnw verify -Dopenapi.update=true", COMMITTED)
                .isEqualTo(actual);
    }
}
