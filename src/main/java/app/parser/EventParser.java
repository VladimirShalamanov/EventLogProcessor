package app.parser;

import app.models.ClickEvent;
import app.models.Event;
import app.models.LoginEvent;
import app.models.LogoutEvent;
import app.models.PurchaseEvent;
import app.models.ViewEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EventParser {

    private static final Set<String> KNOWN_ACTIONS = Set.of(
            "login", "logout", "view", "click", "purchase"
    );

    private final ObjectMapper objectMapper;
    private int invalidLinesCount;

    public EventParser() {
        this.objectMapper = new ObjectMapper();
        this.invalidLinesCount = 0;
    }

    public List<Event> parseLogFile(String filePath) throws IOException {
        List<Event> validEvents = new ArrayList<>();
        Set<String> processedEventIds = new HashSet<>();
        invalidLinesCount = 0;

        try (BufferedReader reader = Files.newBufferedReader(Path.of(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line, validEvents, processedEventIds);
            }
        }

        return validEvents;
    }

    public int getInvalidLinesCount() {
        return invalidLinesCount;
    }

    private void parseLine(String line, List<Event> validEvents, Set<String> processedEventIds) {
        try {
            Event event = objectMapper.readValue(line, Event.class);
            if (isValid(event)) {
                String eventId = event.getEventId().toString();
                if (processedEventIds.contains(eventId)) {
                    invalidLinesCount++;
                } else {
                    processedEventIds.add(eventId);
                    validEvents.add(event);
                }
            } else {
                invalidLinesCount++;
            }
        } catch (JsonProcessingException e) {
            invalidLinesCount++;
        }
    }

    private boolean isValid(Event event) {
        if (event == null) {
            return false;
        }

        if (!hasRequiredFields(event)) {
            return false;
        }

        if (!isValidTimestamp(event.getTimestamp())) {
            return false;
        }

        if (!isValidUuid(event.getEventId()) || !isValidUuid(event.getUserId())) {
            return false;
        }

        if (!isKnownAction(event.getAction()) || !matchesActionType(event)) {
            return false;
        }

        return validateActionSpecificFields(event);
    }

    private boolean hasRequiredFields(Event event) {
        return event.getTimestamp() != null && !event.getTimestamp().isBlank()
                && event.getEventId() != null
                && event.getUserId() != null
                && event.getAction() != null && !event.getAction().isBlank();
    }

    private boolean isValidTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return false;
        }
        try {
            Instant.parse(timestamp);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean isValidUuid(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        try {
            UUID.fromString(uuid.toString());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isKnownAction(String action) {
        return KNOWN_ACTIONS.contains(action);
    }

    private boolean matchesActionType(Event event) {
        return switch (event.getAction()) {
            case "login" -> event instanceof LoginEvent;
            case "logout" -> event instanceof LogoutEvent;
            case "view" -> event instanceof ViewEvent;
            case "click" -> event instanceof ClickEvent;
            case "purchase" -> event instanceof PurchaseEvent;
            default -> false;
        };
    }

    private boolean validateActionSpecificFields(Event event) {
        return switch (event) {
            case ViewEvent view -> view.getArticleId() != null && !view.getArticleId().isBlank();
            case ClickEvent click -> click.getTarget() != null && !click.getTarget().isBlank();
            case PurchaseEvent purchase -> purchase.getAmount() != null;
            case LoginEvent login -> true;
            case LogoutEvent logout -> true;
            default -> false;
        };
    }
}
