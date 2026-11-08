package dev.likezendesk.identity.web;

import dev.likezendesk.identity.app.Authentication;
import dev.likezendesk.shared.ApiException;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * コントローラーの {@link Viewer} 型の引数を、URL のテナント識別子とセッションのログイン状態から作る。
 *
 * <ul>
 *   <li>テナントがない: 404
 *   <li>そのテナントにログインしていない、またはユーザーが無効: 401
 *   <li>ロールが引数の型に合わない（依頼者が {@code Viewer.Staff} の API を呼んだなど）: 404。リソースの存在を漏らさない。
 * </ul>
 */
@Component
class ViewerArgumentResolver implements HandlerMethodArgumentResolver {

    private final Tenants tenants;
    private final Authentication authentication;

    ViewerArgumentResolver(Tenants tenants, Authentication authentication) {
        this.tenants = tenants;
        this.authentication = authentication;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Viewer.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        var request = webRequest.getNativeRequest(HttpServletRequest.class);
        @SuppressWarnings("unchecked")
        var pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        var slug = pathVariables == null ? null : pathVariables.get("slug");
        if (slug == null) {
            throw new IllegalStateException("Viewer parameters require a {slug} path variable: " + parameter);
        }
        var tenant = tenants.require(slug);
        var viewer = LoginSessions.userOf(request, tenant.id())
                .flatMap(userId -> authentication.viewerOf(tenant.id(), userId))
                .orElseThrow(ApiException::unauthenticated);
        if (!parameter.getParameterType().isInstance(viewer)) {
            throw ApiException.notFound();
        }
        return viewer;
    }
}
