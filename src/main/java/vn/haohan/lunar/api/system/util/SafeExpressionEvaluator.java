package vn.haohan.lunar.core.system.util;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;
import net.objecthunter.exp4j.operator.Operator;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.integration.placeholder.LunarPlaceholderResolver;
import vn.haohan.lunar.api.skill.SkillCastContext;
import vn.haohan.lunar.core.system.variable.VariableManager;

import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance, safe mathematical expression evaluator powered by exp4j.
 * Supports +, -, *, /, %, ^, unary minus/plus, parentheses, and math functions:
 * min, max, abs, sin, cos, tan, sqrt, ceil, floor, round, random.
 * Strictly guards against malicious characters, handles division by zero, and provides safe fallbacks.
 */
public final class SafeExpressionEvaluator {

    private static final Logger LOGGER = Logger.getLogger(SafeExpressionEvaluator.class.getName());

    private static final Set<String> ALLOWED_FUNCTIONS = Set.of(
            "min", "max", "abs", "sin", "cos", "tan", "sqrt", "ceil", "floor", "round", "random"
    );

    private static final Function FUNC_MIN = new Function("min", 2) {
        @Override
        public double apply(double... args) {
            return Math.min(args[0], args[1]);
        }
    };

    private static final Function FUNC_MAX = new Function("max", 2) {
        @Override
        public double apply(double... args) {
            return Math.max(args[0], args[1]);
        }
    };

    private static final Function FUNC_ROUND = new Function("round", 1) {
        @Override
        public double apply(double... args) {
            return (double) Math.round(args[0]);
        }
    };

    private static final Function FUNC_RANDOM = new Function("random", 0) {
        @Override
        public double apply(double... args) {
            return Math.random();
        }
    };

    private static final Operator OP_MODULO = new Operator("%", 2, true, Operator.PRECEDENCE_MULTIPLICATION) {
        @Override
        public double apply(double... args) {
            if (args[1] == 0) {
                throw new ArithmeticException("Division by zero in modulo");
            }
            return args[0] % args[1];
        }
    };

    private SafeExpressionEvaluator() {
    }

    /**
     * Evaluates a mathematical expression.
     * Throws {@link IllegalArgumentException} or {@link ArithmeticException} if the expression is invalid.
     */
    public static double evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            return 0.0;
        }

        validateCharacters(expression);

        try {
            Expression exp = buildExpression(expression, null);
            return exp.evaluate();
        } catch (ArithmeticException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse or evaluate math expression: " + expression, e);
        }
    }

    /**
     * Safe evaluate with fallback. Catches all syntax errors and arithmetic exceptions,
     * logs a warning, and returns the fallback value.
     */
    public static double evaluate(String expression, double fallback) {
        return evaluate(expression, null, fallback);
    }

    /**
     * Evaluates an expression with a set of variables and safe fallback.
     */
    public static double evaluate(String expression, Map<String, Double> variables, double fallback) {
        if (expression == null || expression.isBlank()) {
            return fallback;
        }

        try {
            validateCharacters(expression);
            Expression exp = buildExpression(expression, variables);
            return exp.evaluate();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to evaluate math expression '" + expression + "', returning fallback " + fallback + ": " + t.getMessage());
            return fallback;
        }
    }

    /**
     * Resolves placeholders recursively on the expression first, then evaluates safely.
     */
    public static double evaluateWithPlaceholders(
            String expression,
            ActiveLunarMob caster,
            LivingEntity target,
            SkillCastContext context,
            VariableManager variableManager,
            double fallback
    ) {
        if (expression == null || expression.isBlank()) {
            return fallback;
        }

        String resolved = LunarPlaceholderResolver.resolve(expression, caster, target, context, variableManager);
        return evaluate(resolved, fallback);
    }

    private static void validateCharacters(String expression) {
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isDigit(c) && c != '.'
                    && c != '+' && c != '-' && c != '*' && c != '/' && c != '%' && c != '^'
                    && c != '(' && c != ')' && c != ','
                    && !Character.isLetter(c) && c != '_') {
                throw new IllegalArgumentException("Illegal character in math expression: " + c);
            }
        }
    }

    private static Expression buildExpression(String expression, Map<String, Double> variables) {
        ExpressionBuilder builder = new ExpressionBuilder(expression)
                .operator(OP_MODULO)
                .function(FUNC_MIN)
                .function(FUNC_MAX)
                .function(FUNC_ROUND)
                .function(FUNC_RANDOM);

        if (variables != null && !variables.isEmpty()) {
            builder.variables(variables.keySet());
            Expression exp = builder.build();
            for (Map.Entry<String, Double> entry : variables.entrySet()) {
                exp.setVariable(entry.getKey(), entry.getValue());
            }
            return exp;
        }

        return builder.build();
    }
}
