package dev.likezendesk.ticket.infra;

import java.io.IOException;
import java.io.InputStream;

/** 添付の中身の保存先（architecture.md 2.9、D10）。キーはサーバーが作る UUID で、利用者の入力をパスに使わない。 */
public interface BlobStore {

    /** {@code content} を書き、書いたバイト数を返す。{@code maxBytes} を超えたら書いた分を消して {@link TooLarge}。 */
    long put(String key, InputStream content, long maxBytes) throws IOException;

    InputStream open(String key) throws IOException;

    /** なければ何もしない。 */
    void delete(String key) throws IOException;

    final class TooLarge extends IOException {
        public TooLarge() {
            super("The content exceeds the size limit");
        }
    }
}
