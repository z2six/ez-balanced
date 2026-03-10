package net.z2six.ezbalance.balance;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EzBalanceItemGroupDefinition {
    public String id = "";
    public String name = "";
    public Map<String, Double> attributeValues = new LinkedHashMap<>();
    public boolean forceDisabledEnchants;
    public java.util.Set<String> allowedEnchantments = new java.util.LinkedHashSet<>();

    public EzBalanceItemGroupDefinition() {}

    public EzBalanceItemGroupDefinition(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
