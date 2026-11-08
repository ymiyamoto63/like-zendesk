package dev.likezendesk.ticket.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** ローカルのディレクトリ（compose では Docker ボリューム）に保存する。 */
@Component
class LocalBlobStore implements BlobStore {

    private static final Pattern KEY = Pattern.compile("[0-9a-f-]{36}");

    private final Path root;

    LocalBlobStore(@Value("${app.attachments.dir:data/attachments}") String root) throws IOException {
        this.root = Files.createDirectories(Path.of(root)).toRealPath();
    }

    @Override
    public long put(String key, InputStream content, long maxBytes) throws IOException {
        var path = path(key);
        long written = 0;
        try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            var buffer = new byte[64 * 1024];
            int read;
            while ((read = content.read(buffer)) != -1) {
                written += read;
                if (written > maxBytes) {
                    throw new TooLarge();
                }
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Files.deleteIfExists(path);
            throw e;
        }
        return written;
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(path(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(path(key));
    }

    private Path path(String key) {
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return root.resolve(key);
    }
}
