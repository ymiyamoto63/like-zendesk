package dev.likezendesk.tenant.domain;

import dev.likezendesk.shared.TenantId;

public record Tenant(TenantId id, String slug, String name, RequesterSignup requesterSignup) {}
