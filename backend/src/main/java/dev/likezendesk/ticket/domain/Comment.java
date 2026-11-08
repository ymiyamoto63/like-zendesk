package dev.likezendesk.ticket.domain;

import java.time.Instant;
import java.util.UUID;

/** コメント（F-C-1）。追記のみで、編集・削除はできない（F-C-2）。{@code isPublic} が false なら社内メモ。 */
public record Comment(UUID id, Ref author, String body, boolean isPublic, Instant createdAt) {}
