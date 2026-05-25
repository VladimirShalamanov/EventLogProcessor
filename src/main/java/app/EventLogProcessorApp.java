package app;

import app.models.Event;
import app.parser.EventParser;
import app.processor.Statistics;

import java.io.IOException;
import java.util.List;

public class EventLogProcessorApp {

    public static void main(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            System.err.println("Error: Please provide the input file path as an argument.");
            System.exit(1);
        }

        String filePath = args[0];

        try {
            EventParser parser = new EventParser();
            List<Event> validEvents = parser.parseLogFile(filePath);

            Statistics statistics = new Statistics();
            for (Event event : validEvents) {
                statistics.processEvent(event);
            }

            statistics.printReport(parser.getInvalidLinesCount());
            statistics.saveSummaryToJson("summary.json");
        } catch (IOException e) {
            System.err.println("Error: Failed to read input file: " + e.getMessage());
            System.exit(1);
        }
    }
}
