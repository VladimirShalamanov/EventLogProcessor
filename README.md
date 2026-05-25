# Event Log Processor

A lightweight and efficient Java console application designed to parse, validate, and aggregate data from streaming event logs encoded in JSON Lines (JSONL) format.

## How to Run the Application

### Requirements
* Java 17 or higher
* Maven (for manual builds)

### Running
1. Open the project in IntelliJ IDEA.
2. Open **"Edit Configurations"** (Run menu) and enter `input.jsonl` in **"Program arguments"**.
3. Build and run the project (`EventLogProcessorApp`).
4. Run unit tests via the `src/test/java` directory.

    * ### Docker Commands
    * `docker build -t event-log-processor .`
    * `docker run --rm -v "${PWD}:/data" event-log-processor /data/input.jsonl`

### Key activities:
* Streaming large files efficiently
* Docker support
* Deduplication by eventId
* JSON summary output (Generates a clean summary.json report)

## Design Decisions
* Streaming Architecture: Instead of loading the entire log file into memory, the app uses BufferedReader to stream and process logs line-by-line. This allows the application to handle massive datasets.

* Polymorphic Deserialization: Leveraged Jackson's @JsonTypeInfo and @JsonSubTypes features to cleanly map incoming JSON structures into specific object types (LoginEvent, PurchaseEvent, etc.) based on the action field.

* Financial Precision: Used BigDecimal with RoundingMode.HALF_UP for all monetary statistics to guarantee mathematical correctness and prevent floating-point calculation issues.

## Assumptions Made
* File Encoding: The input log file is assumed to be encoded in standard UTF-8.

* Unique Events: Duplicate eventId fields within the same execution run are treated as invalid data entries to safeguard statistics integrity (Deduplication).

* Missing Fields as Errors: If a structurally required action field (like target for click events) is missing or blank, the row is treated as non-recoverable and marked invalid.

## Tradeoffs Considered
* In-Memory Statistics State: For simplicity as a console application, user metrics and counts are stored in-memory using HashMap and LinkedHashMap. For an enterprise system with persistent massive streams, this state would ideally be offloaded to an external cache like Redis or an event broker like Kafka.

* Validation Strategy: Chose a multi-layered explicit validation strategy over pure Jackson automated mappings. This makes debugging validation failures much easier and matches the specific business requirements perfectly.

### Validation Rules:
- it is not valid JSON
- eventId is not a valid UUID
- userId is not a valid UUID
- required fields are missing
- the action is unknown
- purchase events contain an invalid amount
- click events are missing target
- view events are missing articleId
- timestamp is missing or invalid
- userId is empty
- invalid lines should NOT stop the program.
- the application should continue processing the remaining lines.