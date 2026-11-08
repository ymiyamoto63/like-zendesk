package dev.likezendesk.ticket.domain;

import java.util.UUID;

/** コメントの添付ファイル（F-T-7）。中身は {@code storageKey} で BlobStore から読む。 */
public record Attachment(UUID id, String filename, String contentType, long size, String storageKey) {

    /** 1 ファイルの上限（DB の CHECK 制約と同じ）。 */
    public static final long MAX_SIZE = 20L * 1024 * 1024;

    /** 1 つのコメントに付けられる数。 */
    public static final int MAX_PER_COMMENT = 10;

    /** パスの部分と制御文字を除き、255 文字までにする。空になったら {@code file}。 */
    public static String safeFilename(@org.jspecify.annotations.Nullable String original) {
        var name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "file";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    /** {@code type/subtype} の形でなければ {@code application/octet-stream}。パラメーター（charset など）は落とす。 */
    public static String safeContentType(@org.jspecify.annotations.Nullable String original) {
        if (original == null) {
            return "application/octet-stream";
        }
        var type = original.split(";", 2)[0].strip().toLowerCase(java.util.Locale.ROOT);
        return type.matches("[a-z0-9][a-z0-9!#$&^_.+-]{0,63}/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}")
                ? type
                : "application/octet-stream";
    }
}
