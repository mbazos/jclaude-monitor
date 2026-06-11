package com.mbazos.jclaude.service;

import com.mbazos.jclaude.model.WebUsageResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeWebClientTest {

    @Test
    void personalAccountWithWindows() {
        String body = """
                {
                  "five_hour": {"utilization": 42.5, "resets_at": "2026-06-10T15:00:00+00:00"},
                  "seven_day": {"utilization": 81.0, "resets_at": "2026-06-14T08:30:00+00:00"}
                }
                """;
        WebUsageResult result = ClaudeWebClient.parseResponse(body);
        WebUsageResult.Available a = assertInstanceOf(WebUsageResult.Available.class, result);
        assertEquals(42.5, a.fiveHourUtil());
        assertEquals(81.0, a.sevenDayUtil());
        assertEquals(Instant.parse("2026-06-10T15:00:00Z"), a.fiveHourReset());
        assertEquals(Instant.parse("2026-06-14T08:30:00Z"), a.sevenDayReset());
        assertFalse(a.extraUsageEnabled());
    }

    @Test
    void enterpriseAccountWithExtraUsageOnly() {
        String body = """
                {
                  "extra_usage": {
                    "is_enabled": true,
                    "utilization": 12.3,
                    "monthly_limit": 50000,
                    "used_credits": 6150
                  }
                }
                """;
        WebUsageResult result = ClaudeWebClient.parseResponse(body);
        WebUsageResult.Available a = assertInstanceOf(WebUsageResult.Available.class, result);
        assertTrue(a.extraUsageEnabled());
        assertEquals(12.3, a.extraUsageUtil());
        assertEquals(50000.0, a.monthlyLimitCents());
        assertEquals(6150.0, a.usedCreditsCents());
        assertNull(a.fiveHourUtil());
        assertNull(a.sevenDayUtil());
    }

    @Test
    void malformedJsonIsUnavailable() {
        WebUsageResult result = ClaudeWebClient.parseResponse("{not json");
        assertInstanceOf(WebUsageResult.Unavailable.class, result);
    }

    @Test
    void emptyObjectIsUnavailable() {
        WebUsageResult result = ClaudeWebClient.parseResponse("{}");
        WebUsageResult.Unavailable u = assertInstanceOf(WebUsageResult.Unavailable.class, result);
        assertEquals("claude.ai response format not recognized", u.reason());
    }

    @Test
    void nullResetsAtFallsBackToNow() {
        String body = """
                {"five_hour": {"utilization": 10.0, "resets_at": null}}
                """;
        WebUsageResult result = ClaudeWebClient.parseResponse(body);
        WebUsageResult.Available a = assertInstanceOf(WebUsageResult.Available.class, result);
        assertEquals(10.0, a.fiveHourUtil());
        assertNotNull(a.fiveHourReset());
    }
}
