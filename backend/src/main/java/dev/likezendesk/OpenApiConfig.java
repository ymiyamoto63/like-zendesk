package dev.likezendesk;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.likezendesk.shared.Viewer;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.PathParameter;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CsrfToken;

/** OpenAPI 定義（/api/docs）。フロントエンドの型の生成元になる。 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    static {
        // リクエストから読むのではなく、セッションや Spring Security から作る引数
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Viewer.class, CsrfToken.class);
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("like-zendesk API").version("v1"));
    }

    /**
     * {@code /api/t/{slug}/...} の操作に、パス引数 {@code slug} を定義する。{@link Viewer} を引数に取る操作は slug を
     * {@code @PathVariable} として宣言しないため（ViewerArgumentResolver が読む）。
     */
    @Bean
    OpenApiCustomizer tenantSlugPathParameter() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (!path.contains("{slug}")) {
                return;
            }
            item.readOperations().forEach(operation -> {
                var declared = operation.getParameters() != null
                        && operation.getParameters().stream()
                                .anyMatch(p -> "path".equals(p.getIn()) && "slug".equals(p.getName()));
                if (!declared) {
                    operation.addParametersItem(
                            new PathParameter().name("slug").required(true).schema(new StringSchema()));
                }
            });
        });
    }

    /**
     * DTO の record の項目を必須にする。生成する TypeScript の型が省略可能にならないように。
     *
     * <p>{@code @Nullable} を付けた項目は、リクエスト（名前が {@code Request} で終わる record）では省略可能にし、レスポンスでは必須のまま
     * {@code null} を許す型にする。レスポンスは null の項目も省略せずに返すため。
     */
    @Bean
    ModelConverter recordComponentsAreRequired() {
        return new ModelConverter() {
            @Override
            public @Nullable Schema<?> resolve(
                    AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
                var schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
                var raw = Json.mapper().constructType(type.getType()).getRawClass();
                if (schema == null || !raw.isRecord()) {
                    return schema;
                }
                var ref = schema.get$ref();
                var model =
                        ref == null ? schema : context.getDefinedModels().get(ref.substring(ref.lastIndexOf('/') + 1));
                if (model == null || model.getProperties() == null) {
                    return schema;
                }
                var isRequest = raw.getSimpleName().endsWith("Request");
                for (var component : raw.getRecordComponents()) {
                    var renamed = renamedAs(raw, component.getName());
                    var name = renamed == null || renamed.value().isEmpty() ? component.getName() : renamed.value();
                    Schema<?> property = (Schema<?>) model.getProperties().get(name);
                    if (property == null) {
                        continue;
                    }
                    var nullable = component.getAnnotatedType().isAnnotationPresent(Nullable.class);
                    if (nullable && isRequest) {
                        continue;
                    }
                    model.addRequiredItem(name);
                    if (nullable) {
                        model.getProperties().put(name, allowNull(property));
                    }
                }
                return schema;
            }
        };
    }

    /** record の項目に付けた {@code @JsonProperty} は、フィールドに伝わる。 */
    private static @Nullable JsonProperty renamedAs(Class<?> record, String component) {
        try {
            return record.getDeclaredField(component).getAnnotation(JsonProperty.class);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static Schema<?> allowNull(Schema<?> property) {
        var nullType = new JsonSchema().types(new LinkedHashSet<>(List.of("null")));
        if (property.get$ref() != null) {
            return new JsonSchema().anyOf(List.of(new JsonSchema().$ref(property.get$ref()), nullType));
        }
        if (property.getTypes() == null || property.getTypes().isEmpty()) {
            // 型を決めない項目（監査ログの値など）は、もともと null を含む
            return property;
        }
        property.addType("null");
        return property;
    }
}
