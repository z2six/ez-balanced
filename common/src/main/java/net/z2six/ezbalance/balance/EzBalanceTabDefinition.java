package net.z2six.ezbalance.balance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EzBalanceTabDefinition {
    public String id = "";
    public String title = "";
    public String iconItemId = "minecraft:book";
    public Set<String> requiredAttributes = new LinkedHashSet<>();
    public Set<String> excludedAttributes = new LinkedHashSet<>();
    public boolean requireAllAttributes;
    public Set<String> includeItemIds = new LinkedHashSet<>();
    public Set<String> excludeItemIds = new LinkedHashSet<>();
    public Set<String> includeNamespaces = new LinkedHashSet<>();
    public Set<String> excludeNamespaces = new LinkedHashSet<>();
    public Set<String> includeTags = new LinkedHashSet<>();
    public Set<String> excludeTags = new LinkedHashSet<>();
    public boolean matchAnyTags;
    public Map<String, EzBalanceAttributeRange> attributeRanges = new LinkedHashMap<>();
    public List<String> nameFilters = new ArrayList<>();
    public List<String> excludedNameFilters = new ArrayList<>();
    public List<String> displayAttributes = new ArrayList<>();
    public String searchText = "";

    public EzBalanceTabDefinition() {}

    public EzBalanceTabDefinition(String id, String title, String iconItemId) {
        this.id = id;
        this.title = title;
        this.iconItemId = iconItemId;
    }
}
