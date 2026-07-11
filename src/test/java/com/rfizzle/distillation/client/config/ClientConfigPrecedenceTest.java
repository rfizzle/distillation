package com.rfizzle.distillation.client.config;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.config.DistillationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tier 1 — the server→client fallback precedence {@link ClientDistillationConfig#effective()}
 * implements: server-synced values win while connected, and the local config is the fallback both
 * before a sync arrives and after disconnect. The wire codec is covered by {@code PayloadCodecTest}.
 *
 * <p>The local config is seeded into {@link Distillation}'s private static field by reflection
 * (there is no public setter, and {@link Distillation#reloadConfig()} would read the real on-disk
 * {@code config/distillation.json}), then restored in teardown so no static state leaks across
 * test classes.
 */
class ClientConfigPrecedenceTest {

    // Deliberately non-default (the class default is 3) so the value assertions fail if
    // effective() ever returns a freshly-constructed config instead of the seeded local instance.
    private static final int LOCAL_INGREDIENT_COST = 4;
    private static final int SERVER_INGREDIENT_COST = 6;

    private DistillationConfig localConfig;
    private DistillationConfig priorConfig;

    @BeforeEach
    void seedLocalConfig() throws ReflectiveOperationException {
        localConfig = new DistillationConfig();
        localConfig.batchIngredientCost = LOCAL_INGREDIENT_COST;

        Field field = Distillation.class.getDeclaredField("config");
        field.setAccessible(true);
        priorConfig = (DistillationConfig) field.get(null);
        field.set(null, localConfig);

        ClientDistillationConfig.clear();
    }

    @AfterEach
    void restore() throws ReflectiveOperationException {
        ClientDistillationConfig.clear();

        Field field = Distillation.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(null, priorConfig);
    }

    @Test
    void unsynced_resolvesToLocalConfig() {
        assertNull(ClientDistillationConfig.getServerConfig(), "precondition: no sync received");
        assertSame(localConfig, ClientDistillationConfig.effective(),
                "unsynced client must resolve to the local config");
        assertEquals(LOCAL_INGREDIENT_COST, ClientDistillationConfig.effective().batchIngredientCost,
                "unsynced client must read local values");
    }

    @Test
    void afterSync_serverValuesWin() {
        DistillationConfig serverConfig = new DistillationConfig();
        serverConfig.batchIngredientCost = SERVER_INGREDIENT_COST;

        ClientDistillationConfig.setServerConfig(serverConfig);

        assertSame(serverConfig, ClientDistillationConfig.effective(),
                "synced client must resolve to the server config");
        assertEquals(SERVER_INGREDIENT_COST, ClientDistillationConfig.effective().batchIngredientCost,
                "server value must win over the local value while synced");
    }

    @Test
    void afterDisconnect_fallsBackToLocalConfig() {
        DistillationConfig serverConfig = new DistillationConfig();
        serverConfig.batchIngredientCost = SERVER_INGREDIENT_COST;
        ClientDistillationConfig.setServerConfig(serverConfig);
        assertSame(serverConfig, ClientDistillationConfig.effective(), "precondition: synced value active");

        ClientDistillationConfig.clear();

        assertSame(localConfig, ClientDistillationConfig.effective(),
                "after disconnect the client must fall back to the local config");
        assertEquals(LOCAL_INGREDIENT_COST, ClientDistillationConfig.effective().batchIngredientCost,
                "after disconnect the client must read local values again");
    }
}
