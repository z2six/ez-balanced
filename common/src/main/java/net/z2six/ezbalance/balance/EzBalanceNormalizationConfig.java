package net.z2six.ezbalance.balance;

import java.util.ArrayList;
import java.util.List;

public final class EzBalanceNormalizationConfig {
    public boolean enabled = false;
    public double preserveFactor = 0.35D;
    public double defaultMinPercent = -0.20D;
    public double defaultMaxPercent = 0.20D;
    public Double defaultMinRawOffset;
    public Double defaultMaxRawOffset;
    public List<EzBalanceAttributeNormalizationRule> attributes = new ArrayList<>();
}
