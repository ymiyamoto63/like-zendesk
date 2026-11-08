package dev.likezendesk.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ブラウザと同じように Cookie を保持し、状態を変えるリクエストに CSRF トークンを付ける HTTP クライアント。
 *
 * <p>1 つのインスタンスが 1 つのブラウザに当たる。
 */
public final class ApiClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http;
    private boolean sendCsrfToken = true;

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    public record Response(int status, JsonNode body) {
        public String code() {
            return body.path("code").asString();
        }
    }

    /** CSRF トークンを付けずに送る（CSRF 対策の検査用）。 */
    public ApiClient withoutCsrfToken() {
        sendCsrfToken = false;
        return this;
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    public Response post(String path, Object body) {
        return send(withCsrf(HttpRequest.newBuilder(uri(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
    }

    public Response put(String path, Object body) {
        return send(withCsrf(HttpRequest.newBuilder(uri(path)))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
    }

    public Response patch(String path, Object body) {
        return send(withCsrf(HttpRequest.newBuilder(uri(path)))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
    }

    public Response delete(String path) {
        return send(withCsrf(HttpRequest.newBuilder(uri(path))).DELETE());
    }

    /** multipart/form-data で 1 ファイルを送る（項目名 {@code file}）。 */
    public Response upload(String path, String filename, String contentType, byte[] content) {
        var boundary = "----test" + System.nanoTime();
        var head = ("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                        + "Content-Type: " + contentType + "\r\n\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var tail = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var body = new java.io.ByteArrayOutputStream();
        body.writeBytes(head);
        body.writeBytes(content);
        body.writeBytes(tail);
        return send(withCsrf(HttpRequest.newBuilder(uri(path)))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
    }

    /** JSON ではない応答（ダウンロード）をそのまま受け取る。 */
    public HttpResponse<byte[]> raw(String path) {
        try {
            return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private HttpRequest.Builder withCsrf(HttpRequest.Builder builder) {
        if (!sendCsrfToken) {
            return builder;
        }
        if (csrfCookie() == null) {
            get("/api/csrf");
        }
        return builder.header("X-XSRF-TOKEN", csrfCookie());
    }

    private String csrfCookie() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private Response send(HttpRequest.Builder request) {
        try {
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            var body = response.body().isEmpty() ? JSON.missingNode() : JSON.readTree(response.body());
            return new Response(response.statusCode(), body);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
