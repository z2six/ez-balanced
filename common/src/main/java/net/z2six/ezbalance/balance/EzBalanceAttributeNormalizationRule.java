package net.z2six.ezbalance.balance;

public final class EzBalanceAttributeNormalizationRule {
    public String attributeId = "";
    public double minPercent = -0.20D;
    public double maxPercent = 0.20D;
    public Double minRawOffset;
    public Double maxRawOffset;

    public EzBalanceAttributeNormalizationRule() {}

    public EzBalanceAttributeNormalizationRule(String attributeId) {
        this.attributeId = attributeId;
    }
}
