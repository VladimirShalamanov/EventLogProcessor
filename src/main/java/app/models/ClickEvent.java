package app.models;

public class ClickEvent extends Event {

    private String target;

    public ClickEvent() {
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
}
