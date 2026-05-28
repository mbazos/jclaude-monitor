package com.mbazos.jclaude.model;

public sealed interface PollResult permits PollResult.Success, PollResult.Failure {

    record Success(QuotaResult quota) implements PollResult {}

    record Failure(String message, Throwable cause) implements PollResult {}
}
