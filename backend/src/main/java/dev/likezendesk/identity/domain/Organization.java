package dev.likezendesk.identity.domain;

import java.util.UUID;

/** 依頼者の所属組織（取引先の会社名、社内の部署名。F-A-4）。{@code members} は所属する依頼者の数。 */
public record Organization(UUID id, String name, long members) {}
