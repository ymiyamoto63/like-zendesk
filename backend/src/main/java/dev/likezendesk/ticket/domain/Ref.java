package dev.likezendesk.ticket.domain;

import java.util.UUID;

/** 表示名つきの参照（依頼者、担当グループ、担当者）。監査ログには当時の表示名ごと記録する。 */
public record Ref(UUID id, String name) {}
