package dev.likezendesk;

import dev.likezendesk.shared.Viewer;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.PathParameter;
import java.util.Iterator;
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

    /** DTO の record の項目は、{@code @Nullable} を付けたものを除いて必須とする。生成する TypeScript の型が省略可能にならないように。 */
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
                if (model != null && model.getProperties() != null) {
                    for (var component : raw.getRecordComponents()) {
                        if (model.getProperties().containsKey(component.getName())
                                && !component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                            model.addRequiredItem(component.getName());
                        }
                    }
                }
                return schema;
            }
        };
    }
}
