package net.z2six.ezbalance.balance;

public final class EzBalanceAttributeRange {
    public double min;
    public double max;

    public EzBalanceAttributeRange() {
        this(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public EzBalanceAttributeRange(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public boolean matches(double value) {
        return value >= this.min && value <= this.max;
    }
}
