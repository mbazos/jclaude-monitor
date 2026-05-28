package com.mbazos.jclaude.model;

import java.time.Instant;
import java.util.Optional;

public record SessionInfo(
        String sessionId,
        String cwd,
        Optional<String> status,    // "busy" or "idle"
        Instant startedAt
) {}
