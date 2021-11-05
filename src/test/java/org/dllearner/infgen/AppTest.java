package org.dllearner.infgen;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class AppTest {
    @Test
    public void testRsr() {
        RSR reasoning = Infgen.findReasoning("a:bcd:e", 1);
        assertTrue(reasoning.step == 5);
    }
}
