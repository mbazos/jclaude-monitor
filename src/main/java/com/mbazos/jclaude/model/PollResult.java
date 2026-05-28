package com.mbazos.jclaude.model;

import java.time.Instant;

public sealed interface PollResult permits PollResult.Success, PollResult.Failure {

    record Success(QuotaResult quota) implements PollResult {}

    record Failure(String message, Throwable cause, Instant polledAt) implements PollResult {}
}
