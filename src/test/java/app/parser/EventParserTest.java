package app.parser;

import app.models.ClickEvent;
import app.models.Event;
import app.models.LoginEvent;
import app.models.PurchaseEvent;
import app.models.ViewEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventParserTest {

    private static final String VALID_TIMESTAMP = "2026-05-01T10:00:00Z";
    private static final String VALID_EVENT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String VALID_USER_ID = "c1b7d8f0-1c3a-4d95-8d0d-6df3f1d5b001";
    private static final String SECOND_EVENT_ID = "550e8400-e29b-41d4-a716-446655440001";

    @TempDir
    Path tempDir;

    private EventParser parser;

    @BeforeEach
    void setUp() {
        parser = new EventParser();
    }

    @Test
    void testParseValidJsonLine_ShouldReturnValidEvent() throws IOException {
        Path logFile = writeLogFile(validLoginLine(VALID_EVENT_ID));

        List<Event> events = parser.parseLogFile(logFile.toString());

        assertEquals(1, events.size(), "Exactly one valid event should be parsed");
        assertEquals(0, parser.getInvalidLinesCount(), "No invalid lines expected");

        Event event = events.getFirst();
        assertInstanceOf(LoginEvent.class, event, "Login action should deserialize to LoginEvent");
        assertEquals(VALID_TIMESTAMP, event.getTimestamp());
        assertEquals(UUID.fromString(VALID_EVENT_ID), event.getEventId());
        assertEquals(UUID.fromString(VALID_USER_ID), event.getUserId());
        assertEquals("login", event.getAction());
    }

    @Test
    void testParseInvalidJson_ShouldIncrementInvalidLinesCount() throws IOException {
        Path logFile = writeLogFile("NOT_VALID_JSON");

        List<Event> events = parser.parseLogFile(logFile.toString());

        assertTrue(events.isEmpty(), "Malformed JSON should not produce events");
        assertEquals(1, parser.getInvalidLinesCount(),
                "Malformed JSON should increment invalid line count");
    }

    @Test
    void testMissingRequiredFields_ShouldBeMarkedAsInvalid() throws IOException {
        Path logFile = writeLogFile(
                "{\"eventId\":\"" + VALID_EVENT_ID + "\",\"userId\":\"" + VALID_USER_ID + "\",\"action\":\"login\"}",
                "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"userId\":\"" + VALID_USER_ID + "\",\"action\":\"login\"}",
                "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"eventId\":\"" + VALID_EVENT_ID + "\",\"action\":\"login\"}"
        );

        List<Event> events = parser.parseLogFile(logFile.toString());

        assertTrue(events.isEmpty(), "Lines missing required fields should be rejected");
        assertEquals(3, parser.getInvalidLinesCount(),
                "Each line missing a required field should count as invalid");
    }

    @Test
    void testInvalidUuidFormat_ShouldBeMarkedAsInvalid() throws IOException {
        Path logFile = writeLogFile(
                "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"eventId\":\"NOT-A-UUID\",\"userId\":\""
                        + VALID_USER_ID + "\",\"action\":\"login\"}",
                "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"eventId\":\"" + VALID_EVENT_ID
                        + "\",\"userId\":\"INVALID-USER-ID\",\"action\":\"login\"}"
        );

        List<Event> events = parser.parseLogFile(logFile.toString());

        assertTrue(events.isEmpty(), "Invalid UUID formats should be rejected");
        assertEquals(2, parser.getInvalidLinesCount(),
                "Both invalid eventId and userId lines should count as invalid");
    }

    @Test
    void testUnknownAction_ShouldBeMarkedAsInvalid() throws IOException {
        Path logFile = writeLogFile(
                "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"eventId\":\"" + VALID_EVENT_ID
                        + "\",\"userId\":\"" + VALID_USER_ID + "\",\"action\":\"unknown\"}"
        );

        List<Event> events = parser.parseLogFile(logFile.toString());

        assertTrue(events.isEmpty(), "Unknown actions should not be accepted");
        assertEquals(1, parser.getInvalidLinesCount(),
                "Unknown action line should increment invalid line count");
    }

    @Test
    void testActionSpecificValidation() throws IOException {
        Path logFile = writeLogFile(
                "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"eventId\":\"" + VALID_EVENT_ID
                        + "\",\"userId\":\"" + VALID_USER_ID + "\",\"action\":\"click\"}",
                "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"eventId\":\"" + SECOND_EVENT_ID
                        + "\",\"userId\":\"" + VALID_USER_ID + "\",\"action\":\"view\"}",
                "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"eventId\":\"550e8400-e29b-41d4-a716-446655440002"
                        + "\",\"userId\":\"" + VALID_USER_ID + "\",\"action\":\"purchase\"}"
        );

        List<Event> events = parser.parseLogFile(logFile.toString());

        assertTrue(events.isEmpty(), "Action-specific validation failures should reject all lines");
        assertEquals(3, parser.getInvalidLinesCount(),
                "Click without target, view without articleId, and purchase without amount should be invalid");
    }

    @Test
    void testDuplicateEventId_ShouldIncrementInvalidLinesCount() throws IOException {
        Path logFile = writeLogFile(
                validLoginLine(VALID_EVENT_ID),
                validLoginLine(VALID_EVENT_ID)
        );

        List<Event> events = parser.parseLogFile(logFile.toString());

        assertEquals(1, events.size(), "Only the first occurrence of an eventId should be kept");
        assertEquals(1, parser.getInvalidLinesCount(),
                "Duplicate eventId should be counted as an invalid line");
        assertInstanceOf(LoginEvent.class, events.getFirst());
    }

    @Test
    void testValidActionSpecificEvents_ShouldParseSuccessfully() throws IOException {
        Path logFile = writeLogFile(
                "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"eventId\":\"" + VALID_EVENT_ID
                        + "\",\"userId\":\"" + VALID_USER_ID
                        + "\",\"action\":\"click\",\"target\":\"subscribe-button\"}",
                "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"eventId\":\"" + SECOND_EVENT_ID
                        + "\",\"userId\":\"" + VALID_USER_ID + "\",\"action\":\"view\",\"articleId\":\"art-900\"}",
                "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"eventId\":\"550e8400-e29b-41d4-a716-446655440002"
                        + "\",\"userId\":\"" + VALID_USER_ID + "\",\"action\":\"purchase\",\"amount\":19.99}"
        );

        List<Event> events = parser.parseLogFile(logFile.toString());

        assertEquals(3, events.size(), "Valid action-specific events should all parse");
        assertEquals(0, parser.getInvalidLinesCount());
        assertInstanceOf(ClickEvent.class, events.get(0));
        assertInstanceOf(ViewEvent.class, events.get(1));
        assertInstanceOf(PurchaseEvent.class, events.get(2));
        assertEquals("subscribe-button", ((ClickEvent) events.get(0)).getTarget());
        assertEquals("art-900", ((ViewEvent) events.get(1)).getArticleId());
        assertEquals(0, new java.math.BigDecimal("19.99").compareTo(((PurchaseEvent) events.get(2)).getAmount()));
    }

    private static String validLoginLine(String eventId) {
        return "{\"timestamp\":\"" + VALID_TIMESTAMP + "\",\"eventId\":\"" + eventId
                + "\",\"userId\":\"" + VALID_USER_ID + "\",\"action\":\"login\"}";
    }

    private Path writeLogFile(String... lines) throws IOException {
        Path logFile = tempDir.resolve("events.jsonl");
        Files.writeString(logFile, String.join(System.lineSeparator(), lines));
        return logFile;
    }
}
