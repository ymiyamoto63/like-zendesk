package dev.likezendesk.shared;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 利用者に理由を返す例外。{@link ApiExceptionHandler} が RFC 9457 の problem+json に変換し、{@code code} に機械可読な理由を入れる。
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> properties;

    public ApiException(HttpStatus status, String code, String detail) {
        this(status, code, detail, Map.of());
    }

    /** {@code properties} は problem+json に追加する項目。 */
    public ApiException(HttpStatus status, String code, String detail, Map<String, Object> properties) {
        super(detail);
        this.status = status;
        this.code = code;
        this.properties = Map.copyOf(properties);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> properties() {
        return properties;
    }

    /** 見えない・存在しない・権限がない。存在を漏らさないため、どれも同じ 404 にする。 */
    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", "Not found");
    }

    public static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Login required");
    }

    /** 業務ルール違反（422）。 */
    public static ApiException rule(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, detail);
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }
}
