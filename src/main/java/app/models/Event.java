package app.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "action",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = LoginEvent.class, name = "login"),
        @JsonSubTypes.Type(value = LogoutEvent.class, name = "logout"),
        @JsonSubTypes.Type(value = ViewEvent.class, name = "view"),
        @JsonSubTypes.Type(value = ClickEvent.class, name = "click"),
        @JsonSubTypes.Type(value = PurchaseEvent.class, name = "purchase")
})
public abstract class Event {

    private String timestamp;
    private UUID eventId;
    private UUID userId;
    private String action;

    protected Event() {
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
