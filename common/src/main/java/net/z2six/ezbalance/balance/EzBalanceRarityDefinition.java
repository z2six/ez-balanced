package net.z2six.ezbalance.balance;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EzBalanceRarityDefinition {
    public String id = "";
    public String name = "";
    public int color = 0xFFFFFF;
    public Map<String, Double> attributeValues = new LinkedHashMap<>();

    public EzBalanceRarityDefinition() {}

    public EzBalanceRarityDefinition(String id, String name, int color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }
}
