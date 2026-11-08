package dev.likezendesk.identity.web;

import dev.likezendesk.identity.app.Authentication;
import dev.likezendesk.identity.app.Profiles;
import dev.likezendesk.shared.Viewer;
import dev.likezendesk.tenant.app.Tenants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SessionController {

    private final Tenants tenants;
    private final Authentication authentication;
    private final Profiles profiles;

    SessionController(Tenants tenants, Authentication authentication, Profiles profiles) {
        this.tenants = tenants;
        this.authentication = authentication;
        this.profiles = profiles;
    }

    /** XSRF-TOKEN Cookie を発行する。画面は最初の POST の前に呼ぶ。 */
    @GetMapping("/api/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void csrf(CsrfToken token) {
        // トークンを読むと、Spring Security が Cookie を書き出す
        token.getToken();
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    @PostMapping("/api/t/{slug}/session")
    MeResponse login(@PathVariable String slug, @Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        var tenant = tenants.require(slug);
        var viewer = authentication.login(tenant.id(), body.email(), body.password());
        var me = profiles.me(viewer);
        LoginSessions.login(request, tenant.id(), me.id());
        return MeResponse.of(me);
    }

    /** このテナントだけからログアウトする。他のテナントのログイン状態は残す。 */
    @DeleteMapping("/api/t/{slug}/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(Viewer viewer, HttpServletRequest request) {
        LoginSessions.logout(request, viewer.tenantId());
    }

    @GetMapping("/api/t/{slug}/me")
    MeResponse me(Viewer viewer) {
        return MeResponse.of(profiles.me(viewer));
    }
}
