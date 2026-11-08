package dev.likezendesk.identity.web;

import dev.likezendesk.identity.app.Profiles;
import dev.likezendesk.identity.app.Signup;
import dev.likezendesk.tenant.domain.TenantSlugs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SignupController {

    private final Signup signup;
    private final Profiles profiles;

    SignupController(Signup signup, Profiles profiles) {
        this.signup = signup;
        this.profiles = profiles;
    }

    /** パスワードの上限は bcrypt が扱える 72 バイトに合わせる。 */
    record SignupRequest(
            @NotBlank @Size(max = 100) String tenantName,
            @NotBlank @Pattern(regexp = TenantSlugs.PATTERN) String slug,
            @NotBlank @Size(max = 100) String adminName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    record SignupResponse(String slug, MeResponse me) {}

    /** テナントと最初の管理者を作り、その管理者としてログインする（F-X-1）。 */
    @PostMapping("/api/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    SignupResponse signup(@Valid @RequestBody SignupRequest body, HttpServletRequest request) {
        var admin = signup.signup(
                new Signup.Command(body.tenantName(), body.slug(), body.adminName(), body.email(), body.password()));
        LoginSessions.login(request, admin.tenantId(), admin.userId());
        return new SignupResponse(body.slug(), MeResponse.of(profiles.me(admin)));
    }
}
