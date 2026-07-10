package com.rfizzle.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DistillationTest {
    @Test
    void modIdIsDistillation() {
        // MOD_ID is a compile-time String constant, so referencing it here does
        // not trigger Fabric classloading (fabric-api is off the test classpath).
        assertEquals("distillation", Distillation.MOD_ID);
    }
}
