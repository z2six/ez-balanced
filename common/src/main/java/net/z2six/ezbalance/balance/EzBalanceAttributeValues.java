package net.z2six.ezbalance.balance;

import java.util.Map;

public final class EzBalanceAttributeValues {
    private EzBalanceAttributeValues() {}

    public static EzBalanceAttributeValue getAttributeValue(
            EzBalanceConfig config,
            String itemId,
            String attributeId,
            Map<String, Double> baseAttributes,
            Map<String, Double> resolvedAttributes
    ) {
        String normalized = EzBalanceAttributeIds.normalize(attributeId);
        if (normalized.isBlank()) {
            return new EzBalanceAttributeValue("", null, null);
        }

        Map<String, Double> originalAttributes = config == null || itemId == null || itemId.isBlank()
                ? Map.of()
                : config.originalAttributes.getOrDefault(itemId, Map.of());
        Map<String, Double> base = baseAttributes == null ? Map.of() : baseAttributes;
        Map<String, Double> resolved = resolvedAttributes == null ? Map.of() : resolvedAttributes;
        Double originalValue = originalAttributes.containsKey(normalized) ? originalAttributes.get(normalized) : base.get(normalized);
        Double currentValue = resolved.containsKey(normalized) ? resolved.get(normalized) : base.get(normalized);
        if (currentValue == null && originalValue != null) {
            currentValue = originalValue;
        }
        return new EzBalanceAttributeValue(normalized, originalValue, currentValue);
    }
}
