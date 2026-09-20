package vn.haohan.lunar.core.system.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeExpressionEvaluatorTest {

    @Test
    void standardArithmetic() {
        assertEquals(7.0, SafeExpressionEvaluator.evaluate("3 + 4"));
        assertEquals(10.0, SafeExpressionEvaluator.evaluate("2 + 4 * 2"));
        assertEquals(12.0, SafeExpressionEvaluator.evaluate("(2 + 4) * 2"));
        assertEquals(2.5, SafeExpressionEvaluator.evaluate("5 / 2"));
        assertEquals(1.0, SafeExpressionEvaluator.evaluate("7 % 3"));
        assertEquals(8.0, SafeExpressionEvaluator.evaluate("2 ^ 3"));
    }

    @Test
    void unaryAndNegativeNumbers() {
        assertEquals(-5.0, SafeExpressionEvaluator.evaluate("-5"));
        assertEquals(-15.0, SafeExpressionEvaluator.evaluate("-3 * 5"));
        assertEquals(5.0, SafeExpressionEvaluator.evaluate("-(-5)"));
    }

    @Test
    void supportedMathFunctions() {
        assertEquals(4.0, SafeExpressionEvaluator.evaluate("sqrt(16)"));
        assertEquals(5.0, SafeExpressionEvaluator.evaluate("abs(-5)"));
        assertEquals(3.0, SafeExpressionEvaluator.evaluate("floor(3.9)"));
        assertEquals(4.0, SafeExpressionEvaluator.evaluate("ceil(3.1)"));
        assertEquals(4.0, SafeExpressionEvaluator.evaluate("round(3.6)"));
        assertEquals(10.0, SafeExpressionEvaluator.evaluate("max(5, 10)"));
        assertEquals(5.0, SafeExpressionEvaluator.evaluate("min(5, 10)"));
        assertEquals(0.0, SafeExpressionEvaluator.evaluate("sin(0)"), 1e-6);
        assertEquals(1.0, SafeExpressionEvaluator.evaluate("cos(0)"), 1e-6);
        double rand = SafeExpressionEvaluator.evaluate("random()");
        assertTrue(rand >= 0.0 && rand < 1.0);
    }

    @Test
    void nestedFunctionsAndComplexEquations() {
        assertEquals(15.0, SafeExpressionEvaluator.evaluate("min(max(10, 20), 15)"));
        assertEquals(20.0, SafeExpressionEvaluator.evaluate("max(min(5, 10), abs(-20))"));
        assertEquals(39.5, SafeExpressionEvaluator.evaluate("25.0 * 1.5 + (100 - 80) * 0.1"));
    }

    @Test
    void divisionByZeroAndInvalidSyntaxSafeFallback() {
        // Division by zero in safe mode returns fallback
        assertEquals(0.0, SafeExpressionEvaluator.evaluate("10 / 0", 0.0));
        assertEquals(42.0, SafeExpressionEvaluator.evaluate("10 / 0", 42.0));
        assertEquals(5.0, SafeExpressionEvaluator.evaluate("10 % 0", 5.0));

        // Invalid syntax in safe mode returns fallback
        assertEquals(-1.0, SafeExpressionEvaluator.evaluate("bad +++ syntax", -1.0));
        assertEquals(100.0, SafeExpressionEvaluator.evaluate("min(", 100.0));
        assertEquals(50.0, SafeExpressionEvaluator.evaluate("", 50.0));
        assertEquals(50.0, SafeExpressionEvaluator.evaluate(null, 50.0));
    }

    @Test
    void variableEvaluation() {
        Map<String, Double> vars = Map.of("caster_damage", 25.0, "target_hp", 120.0);
        double result = SafeExpressionEvaluator.evaluate("caster_damage * 1.5 + target_hp * 0.1", vars, 0.0);
        assertEquals(49.5, result, 1e-6);
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> SafeExpressionEvaluator.evaluate("System.exit(0)"));
        assertThrows(IllegalArgumentException.class, () -> SafeExpressionEvaluator.evaluate("5 + $bad"));
        assertThrows(IllegalArgumentException.class, () -> SafeExpressionEvaluator.evaluate("2 * ; rm -rf"));
    }
}
