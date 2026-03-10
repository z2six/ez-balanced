package net.z2six.ezbalance.balance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class EzBalanceJson {

    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private EzBalanceJson() {}
}
