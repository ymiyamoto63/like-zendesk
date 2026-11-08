package dev.likezendesk.identity.web;

import dev.likezendesk.identity.app.PasswordResets;
import dev.likezendesk.identity.app.Profiles;
import dev.likezendesk.identity.app.RequesterRegistration;
import dev.likezendesk.tenant.app.Tenants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 依頼者の自己登録とメールアドレスの確認（F-A-2）、パスワードの再設定。 */
@RestController
class RegistrationController {

    private final Tenants tenants;
    private final RequesterRegistration registration;
    private final PasswordResets passwordResets;
    private final Profiles profiles;

    RegistrationController(
            Tenants tenants, RequesterRegistration registration, PasswordResets passwordResets, Profiles profiles) {
        this.tenants = tenants;
        this.registration = registration;
        this.passwordResets = passwordResets;
        this.profiles = profiles;
    }

    record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    /** 登録済みのアドレスでも 202 を返し、登録の有無を漏らさない。 */
    @PostMapping("/api/t/{slug}/requesters")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void register(@PathVariable String slug, @Valid @RequestBody RegisterRequest body) {
        registration.register(tenants.require(slug), body.name(), body.email(), body.password());
    }

    record VerifyRequest(@NotBlank String token) {}

    /** メールアドレスを確認し、そのユーザーとしてログインする。 */
    @PostMapping("/api/t/{slug}/email-verifications")
    MeResponse verify(@PathVariable String slug, @Valid @RequestBody VerifyRequest body, HttpServletRequest request) {
        var tenant = tenants.require(slug);
        var me = profiles.me(registration.verify(tenant, body.token()));
        LoginSessions.login(request, tenant.id(), me.id());
        return MeResponse.of(me);
    }

    record ResetRequest(@NotBlank @Email @Size(max = 254) String email) {}

    /** ユーザーがいなくても 202 を返す。 */
    @PostMapping("/api/t/{slug}/password-resets")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestReset(@PathVariable String slug, @Valid @RequestBody ResetRequest body) {
        passwordResets.request(tenants.require(slug), body.email());
    }

    record NewPasswordRequest(
            @NotBlank @Size(min = 8, max = 72) String password) {}

    @PutMapping("/api/t/{slug}/password-resets/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@PathVariable String slug, @PathVariable String token, @Valid @RequestBody NewPasswordRequest body) {
        passwordResets.reset(tenants.require(slug), token, body.password());
    }
}
