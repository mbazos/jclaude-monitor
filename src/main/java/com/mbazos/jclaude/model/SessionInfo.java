package com.mbazos.jclaude.model;

import java.time.Instant;

public record SessionInfo(
        String sessionId,
        String cwd,
        String status,    // "busy", "idle", or null
        Instant startedAt
) {}
