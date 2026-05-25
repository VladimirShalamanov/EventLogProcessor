package app.models;

import java.math.BigDecimal;

public class PurchaseEvent extends Event {

    private BigDecimal amount;

    public PurchaseEvent() {
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
