package app.processor;

import app.models.Event;
import app.models.PurchaseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Statistics {

    private static final List<String> ACTION_DISPLAY_ORDER = List.of(
            "login", "logout", "view", "click", "purchase"
    );

    private final ObjectMapper objectMapper;
    private final Map<String, Integer> eventCountPerAction;
    private final Map<String, Integer> userEventCounts;
    private BigDecimal totalPurchaseAmount;
    private BigDecimal maxPurchaseAmount;

    private int totalValidEvents;
    private int purchaseCount;
    private int totalInvalidLines;

    public Statistics() {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.eventCountPerAction = new HashMap<>();
        this.userEventCounts = new HashMap<>();
        this.totalPurchaseAmount = BigDecimal.ZERO;
        this.maxPurchaseAmount = BigDecimal.ZERO;
    }

    public void processEvent(Event event) {
        totalValidEvents++;

        String action = event.getAction();
        eventCountPerAction.merge(action, 1, Integer::sum);

        String userId = event.getUserId().toString();
        userEventCounts.merge(userId, 1, Integer::sum);

        if (event instanceof PurchaseEvent purchase) {
            BigDecimal amount = purchase.getAmount();
            totalPurchaseAmount = totalPurchaseAmount.add(amount);

            if (amount.compareTo(maxPurchaseAmount) > 0) {
                maxPurchaseAmount = amount;
            }
            purchaseCount++;
        }
    }

    public void printReport(int invalidLinesCount) {
        this.totalInvalidLines = invalidLinesCount;

        System.out.println("Total valid events: " + totalValidEvents);
        System.out.println("Total invalid lines: " + invalidLinesCount);
        System.out.println();

        printEventCountPerUser();
        System.out.println();

        printPurchaseStatistics();
        System.out.println();

        printMostActiveUser();
        System.out.println();

        printTopThreeMostActiveUsers();
        System.out.println();

        printEventCountPerAction();
    }

    public void saveSummaryToJson(String outputPath) throws IOException {
        StatisticsSummary summary = buildSummary();
        objectMapper.writeValue(Path.of(outputPath).toFile(), summary);
    }

    private StatisticsSummary buildSummary() {
        StatisticsSummary summary = new StatisticsSummary();

        summary.setTotalValidEvents(totalValidEvents);
        summary.setTotalInvalidLines(totalInvalidLines);
        summary.setEventCountPerUser(getSortedUserEventCounts());
        summary.setPurchaseStatistics(buildPurchaseStatistics());
        summary.setMostActiveUser(findMostActiveUserId());
        summary.setTopActiveUsers(buildTopActiveUsers());
        summary.setEventCountPerAction(buildOrderedActionCounts());

        return summary;
    }

    private Map<String, Integer> getSortedUserEventCounts() {
        return userEventCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private PurchaseStatistics buildPurchaseStatistics() {
        PurchaseStatistics purchaseStatistics = new PurchaseStatistics();

        purchaseStatistics.setTotal(formatAmount(totalPurchaseAmount));
        purchaseStatistics.setAverage(formatAmount(calculateAveragePurchaseAmount()));
        purchaseStatistics.setLargest(formatAmount(maxPurchaseAmount));

        return purchaseStatistics;
    }

    private String findMostActiveUserId() {
        return userEventCounts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry<String, Integer>::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<TopActiveUser> buildTopActiveUsers() {
        AtomicInteger rank = new AtomicInteger(1);

        return userEventCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(entry -> new TopActiveUser(
                        rank.getAndIncrement(),
                        entry.getKey(),
                        entry.getValue()))
                .toList();
    }

    private Map<String, Integer> buildOrderedActionCounts() {
        Map<String, Integer> orderedActionCounts = new LinkedHashMap<>();

        ACTION_DISPLAY_ORDER.forEach(action ->
                orderedActionCounts.put(action, eventCountPerAction.getOrDefault(action, 0)));

        return orderedActionCounts;
    }

    private void printEventCountPerUser() {
        getSortedUserEventCounts().forEach((userId, count) ->
                System.out.println(userId + ": " + count));
    }

    private void printPurchaseStatistics() {
        System.out.println("Total purchase amount: " + formatAmount(totalPurchaseAmount));
        System.out.println("Average purchase amount: " + formatAmount(calculateAveragePurchaseAmount()));
        System.out.println("Largest purchase: " + formatAmount(maxPurchaseAmount));
    }

    private BigDecimal calculateAveragePurchaseAmount() {
        if (purchaseCount == 0) {
            return BigDecimal.ZERO;
        }

        return totalPurchaseAmount.divide(
                BigDecimal.valueOf(purchaseCount),
                2,
                RoundingMode.HALF_UP
        );
    }

    private void printMostActiveUser() {
        String mostActiveUser = findMostActiveUserId();

        if (mostActiveUser != null) {
            System.out.println("Most active user: " + mostActiveUser);
        }
    }

    private void printTopThreeMostActiveUsers() {
        buildTopActiveUsers().forEach(user ->
                System.out.println(user.getRank() + ". " + user.getUserId()
                        + " - " + user.getEventCount() + " events"));
    }

    private void printEventCountPerAction() {
        buildOrderedActionCounts().forEach((action, count) ->
                System.out.println(action + ": " + count));
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static class StatisticsSummary {

        private int totalValidEvents;
        private int totalInvalidLines;
        private List<TopActiveUser> topActiveUsers;
        private Map<String, Integer> eventCountPerAction;
        private Map<String, Integer> eventCountPerUser;
        private PurchaseStatistics purchaseStatistics;
        private String mostActiveUser;

        public int getTotalValidEvents() {
            return totalValidEvents;
        }

        public void setTotalValidEvents(int totalValidEvents) {
            this.totalValidEvents = totalValidEvents;
        }

        public int getTotalInvalidLines() {
            return totalInvalidLines;
        }

        public void setTotalInvalidLines(int totalInvalidLines) {
            this.totalInvalidLines = totalInvalidLines;
        }

        public Map<String, Integer> getEventCountPerUser() {
            return eventCountPerUser;
        }

        public void setEventCountPerUser(Map<String, Integer> eventCountPerUser) {
            this.eventCountPerUser = eventCountPerUser;
        }

        public PurchaseStatistics getPurchaseStatistics() {
            return purchaseStatistics;
        }

        public void setPurchaseStatistics(PurchaseStatistics purchaseStatistics) {
            this.purchaseStatistics = purchaseStatistics;
        }

        public String getMostActiveUser() {
            return mostActiveUser;
        }

        public void setMostActiveUser(String mostActiveUser) {
            this.mostActiveUser = mostActiveUser;
        }

        public List<TopActiveUser> getTopActiveUsers() {
            return topActiveUsers;
        }

        public void setTopActiveUsers(List<TopActiveUser> topActiveUsers) {
            this.topActiveUsers = topActiveUsers;
        }

        public Map<String, Integer> getEventCountPerAction() {
            return eventCountPerAction;
        }

        public void setEventCountPerAction(Map<String, Integer> eventCountPerAction) {
            this.eventCountPerAction = eventCountPerAction;
        }
    }

    public static class PurchaseStatistics {

        private String total;
        private String average;
        private String largest;

        public String getTotal() {
            return total;
        }

        public void setTotal(String total) {
            this.total = total;
        }

        public String getAverage() {
            return average;
        }

        public void setAverage(String average) {
            this.average = average;
        }

        public String getLargest() {
            return largest;
        }

        public void setLargest(String largest) {
            this.largest = largest;
        }
    }

    public static class TopActiveUser {

        private int rank;
        private String userId;
        private int eventCount;

        public TopActiveUser() {
        }

        public TopActiveUser(int rank, String userId, int eventCount) {
            this.rank = rank;
            this.userId = userId;
            this.eventCount = eventCount;
        }

        public int getRank() {
            return rank;
        }

        public void setRank(int rank) {
            this.rank = rank;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public int getEventCount() {
            return eventCount;
        }

        public void setEventCount(int eventCount) {
            this.eventCount = eventCount;
        }
    }
}
