package app.processor;

import app.models.ClickEvent;
import app.models.Event;
import app.models.LoginEvent;
import app.models.LogoutEvent;
import app.models.PurchaseEvent;
import app.models.ViewEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class StatisticsTest {

    private static final String TIMESTAMP = "2026-05-01T10:00:00Z";
    private static final List<String> EXPECTED_ACTION_ORDER = List.of(
            "login", "logout", "view", "click", "purchase"
    );

    @TempDir
    Path tempDir;

    private Statistics statistics;
    private ObjectMapper objectMapper;
    private int eventIdSequence;

    @BeforeEach
    void setUp() {
        statistics = new Statistics();
        objectMapper = new ObjectMapper();
        eventIdSequence = 0;
    }

    @Test
    void testProcessEvent_ShouldIncrementTotalValidEventsAndActionCounts() throws IOException {
        statistics.processEvent(createLogin("c1b7d8f0-1c3a-4d95-8d0d-6df3f1d5b001", "550e8400-e29b-41d4-a716-446655440000"));
        statistics.processEvent(createLogin("c1b7d8f0-1c3a-4d95-8d0d-6df3f1d5b001", "550e8400-e29b-41d4-a716-446655440001"));
        statistics.processEvent(createView("c1b7d8f0-1c3a-4d95-8d0d-6df3f1d5b001", "550e8400-e29b-41d4-a716-446655440002"));

        Statistics.StatisticsSummary summary = buildSummary();

        assertEquals(3, summary.getTotalValidEvents(), "Total valid events should match processed count");
        assertEquals(2, summary.getEventCountPerAction().get("login"), "Login count should be 2");
        assertEquals(1, summary.getEventCountPerAction().get("view"), "View count should be 1");
        assertEquals(0, summary.getEventCountPerAction().get("logout"), "Unused actions should be zero");
    }

    @Test
    void testPurchaseStatistics_ShouldCalculateCorrectTotalAverageAndMax() throws IOException {
        statistics.processEvent(createPurchase("d2d44db8-b8d9-4b43-9c2f-3bb47e87f221",
                "550e8400-e29b-41d4-a716-446655440010", new BigDecimal("10.00")));
        statistics.processEvent(createPurchase("d2d44db8-b8d9-4b43-9c2f-3bb47e87f221",
                "550e8400-e29b-41d4-a716-446655440011", new BigDecimal("20.00")));
        statistics.processEvent(createPurchase("d2d44db8-b8d9-4b43-9c2f-3bb47e87f221",
                "550e8400-e29b-41d4-a716-446655440012", new BigDecimal("15.11")));

        Statistics.PurchaseStatistics purchaseStats = buildSummary().getPurchaseStatistics();

        assertEquals("45.11", purchaseStats.getTotal(), "Total purchase amount should sum all purchases");
        assertEquals("15.04", purchaseStats.getAverage(),
                "Average should use HALF_UP rounding to two decimal places");
        assertEquals("20.00", purchaseStats.getLargest(), "Largest purchase should be the maximum amount");
    }

    @Test
    void testMostActiveUserAndTopThree_ShouldSortCorrectly() throws IOException {
        String userA = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String userB = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        String userC = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        String userF = "ffffffff-ffff-ffff-ffff-ffffffffffff";

        addEventsForUser(userA, 3);
        addEventsForUser(userF, 3);
        addEventsForUser(userB, 2);
        addEventsForUser(userC, 1);

        Statistics.StatisticsSummary summary = buildSummary();

        assertEquals(userF, summary.getMostActiveUser(),
                "Most active user should be the lexicographically greatest userId among tied top counts");

        List<Statistics.TopActiveUser> topThree = summary.getTopActiveUsers();
        assertEquals(3, topThree.size(), "Top three list should contain exactly three users");

        assertEquals(1, topThree.get(0).getRank());
        assertEquals(userA, topThree.get(0).getUserId());
        assertEquals(3, topThree.get(0).getEventCount());

        assertEquals(2, topThree.get(1).getRank());
        assertEquals(userF, topThree.get(1).getUserId());
        assertEquals(3, topThree.get(1).getEventCount());

        assertEquals(3, topThree.get(2).getRank());
        assertEquals(userB, topThree.get(2).getUserId());
        assertEquals(2, topThree.get(2).getEventCount());
    }

    @Test
    void testBuildOrderedActionCounts_ShouldMaintainInsertionOrder() throws IOException {
        statistics.processEvent(createPurchase("c1b7d8f0-1c3a-4d95-8d0d-6df3f1d5b001",
                "550e8400-e29b-41d4-a716-446655440000", new BigDecimal("9.99")));

        statistics.processEvent(createClick("c1b7d8f0-1c3a-4d95-8d0d-6df3f1d5b001",
                "550e8400-e29b-41d4-a716-446655440001"));

        statistics.processEvent(createView("c1b7d8f0-1c3a-4d95-8d0d-6df3f1d5b001",
                "550e8400-e29b-41d4-a716-446655440002"));

        statistics.processEvent(createLogout("c1b7d8f0-1c3a-4d95-8d0d-6df3f1d5b001",
                "550e8400-e29b-41d4-a716-446655440003"));

        statistics.processEvent(createLogin("c1b7d8f0-1c3a-4d95-8d0d-6df3f1d5b001",
                "550e8400-e29b-41d4-a716-446655440004"));

        Map<String, Integer> actionCounts = buildSummary().getEventCountPerAction();

        assertIterableEquals(EXPECTED_ACTION_ORDER, new ArrayList<>(actionCounts.keySet()),
                "Action counts should follow login, logout, view, click, purchase order");
        assertEquals(1, actionCounts.get("login"));
        assertEquals(1, actionCounts.get("logout"));
        assertEquals(1, actionCounts.get("view"));
        assertEquals(1, actionCounts.get("click"));
        assertEquals(1, actionCounts.get("purchase"));
    }

    private Statistics.StatisticsSummary buildSummary() throws IOException {
        statistics.printReport(0);

        Path summaryPath = tempDir.resolve("summary.json");
        statistics.saveSummaryToJson(summaryPath.toString());

        return objectMapper.readValue(summaryPath.toFile(), Statistics.StatisticsSummary.class);
    }

    private void addEventsForUser(String userId, int eventCount) {
        for (int i = 0; i < eventCount; i++) {
            String eventId = String.format("550e8400-e29b-41d4-a716-%012d", eventIdSequence++);
            statistics.processEvent(createLogin(userId, eventId));
        }
    }

    private LoginEvent createLogin(String userId, String eventId) {
        LoginEvent event = new LoginEvent();
        populateBaseFields(event, userId, eventId, "login");

        return event;
    }

    private LogoutEvent createLogout(String userId, String eventId) {
        LogoutEvent event = new LogoutEvent();
        populateBaseFields(event, userId, eventId, "logout");

        return event;
    }

    private ViewEvent createView(String userId, String eventId) {
        ViewEvent event = new ViewEvent();

        populateBaseFields(event, userId, eventId, "view");
        event.setArticleId("art-100");

        return event;
    }

    private ClickEvent createClick(String userId, String eventId) {
        ClickEvent event = new ClickEvent();

        populateBaseFields(event, userId, eventId, "click");
        event.setTarget("button");

        return event;
    }

    private PurchaseEvent createPurchase(String userId, String eventId, BigDecimal amount) {
        PurchaseEvent event = new PurchaseEvent();

        populateBaseFields(event, userId, eventId, "purchase");
        event.setAmount(amount);

        return event;
    }

    private void populateBaseFields(Event event, String userId, String eventId, String action) {
        event.setTimestamp(TIMESTAMP);
        event.setUserId(UUID.fromString(userId));
        event.setEventId(UUID.fromString(eventId));
        event.setAction(action);
    }
}
