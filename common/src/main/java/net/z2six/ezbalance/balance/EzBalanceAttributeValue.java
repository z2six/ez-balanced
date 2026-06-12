package net.z2six.ezbalance.balance;

public record EzBalanceAttributeValue(String attributeId, Double originalValue, Double currentValue) {
    private static final double EPSILON = 0.00001D;

    public boolean hasOriginalValue() {
        return this.originalValue != null;
    }

    public boolean hasCurrentValue() {
        return this.currentValue != null;
    }

    public boolean isChanged() {
        if (this.originalValue == null && this.currentValue == null) {
            return false;
        }
        if (this.originalValue == null || this.currentValue == null) {
            return true;
        }
        return Math.abs(this.originalValue - this.currentValue) > EPSILON;
    }
}
