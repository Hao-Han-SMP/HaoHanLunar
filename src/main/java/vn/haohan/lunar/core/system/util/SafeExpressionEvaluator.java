package vn.haohan.lunar.core.system.util;

import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.integration.placeholder.PlaceholderResolver;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.core.system.variable.VariableManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance, safe mathematical expression evaluator powered by a zero-dependency AST parser.
 * Supports +, -, *, /, %, ^, unary minus/plus, parentheses, and math functions:
 * min, max, abs, sin, cos, tan, sqrt, ceil, floor, round, random.
 * Strictly guards against malicious characters, handles division by zero, and provides safe fallbacks.
 * Features concurrent token and constant caching to reduce GC pressure and allocation in high-frequency tick loops.
 */
public final class SafeExpressionEvaluator {

    private static final Logger LOGGER = Logger.getLogger(SafeExpressionEvaluator.class.getName());

    private static final Map<String, List<Token>> TOKEN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Double> CONSTANT_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1024;

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
            return parseAndEvaluate(expression, null);
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
            return parseAndEvaluate(expression, variables);
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
            ActiveMob caster,
            LivingEntity target,
            SkillCastContext context,
            VariableManager variableManager,
            double fallback
    ) {
        if (expression == null || expression.isBlank()) {
            return fallback;
        }

        String resolved = PlaceholderResolver.resolve(expression, caster, target, context, variableManager);
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

    private static double parseAndEvaluate(String expression, Map<String, Double> variables) {
        boolean hasVariables = variables != null && !variables.isEmpty();
        boolean deterministic = !hasVariables && !expression.contains("random");

        if (deterministic) {
            Double cached = CONSTANT_CACHE.get(expression);
            if (cached != null) {
                return cached;
            }
        }

        List<Token> tokens = TOKEN_CACHE.get(expression);
        if (tokens == null) {
            validateCharacters(expression);
            tokens = Parser.tokenize(expression);
            if (TOKEN_CACHE.size() < MAX_CACHE_SIZE) {
                TOKEN_CACHE.put(expression, tokens);
            }
        }

        Parser parser = new Parser(tokens, variables);
        double result = parser.parse();
        if (Double.isInfinite(result) || Double.isNaN(result)) {
            throw new ArithmeticException("Expression evaluated to NaN or Infinity");
        }

        if (deterministic && CONSTANT_CACHE.size() < MAX_CACHE_SIZE) {
            CONSTANT_CACHE.put(expression, result);
        }

        return result;
    }

    // ==========================================
    // Zero-dependency Recursive Descent Parser
    // ==========================================

    private enum TokenType {
        NUMBER, IDENTIFIER, PLUS, MINUS, MULTIPLY, DIVIDE, MODULO, POWER, LPAREN, RPAREN, COMMA, EOF
    }

    private record Token(TokenType type, String text, double numberValue) {

        Token(TokenType type, String text) {
            this(type, text, 0.0);
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final Map<String, Double> variables;
        private int pos;

        Parser(List<Token> tokens, Map<String, Double> variables) {
            this.tokens = tokens;
            this.variables = variables;
            this.pos = 0;
        }

        private static List<Token> tokenize(String input) {
            List<Token> tokens = new ArrayList<>();
            int i = 0;
            int len = input.length();

            while (i < len) {
                char c = input.charAt(i);

                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }

                if (Character.isDigit(c) || (c == '.' && i + 1 < len && Character.isDigit(input.charAt(i + 1)))) {
                    int start = i;
                    boolean hasDot = false;
                    while (i < len) {
                        char ch = input.charAt(i);
                        if (ch == '.') {
                            if (hasDot) break;
                            hasDot = true;
                        } else if (!Character.isDigit(ch)) {
                            break;
                        }
                        i++;
                    }
                    String numStr = input.substring(start, i);
                    double val = Double.parseDouble(numStr);
                    tokens.add(new Token(TokenType.NUMBER, numStr, val));
                    continue;
                }

                if (Character.isLetter(c) || c == '_') {
                    int start = i;
                    while (i < len && (Character.isLetterOrDigit(input.charAt(i)) || input.charAt(i) == '_')) {
                        i++;
                    }
                    String id = input.substring(start, i);
                    tokens.add(new Token(TokenType.IDENTIFIER, id));
                    continue;
                }

                switch (c) {
                    case '+': tokens.add(new Token(TokenType.PLUS, "+")); break;
                    case '-': tokens.add(new Token(TokenType.MINUS, "-")); break;
                    case '*': tokens.add(new Token(TokenType.MULTIPLY, "*")); break;
                    case '/': tokens.add(new Token(TokenType.DIVIDE, "/")); break;
                    case '%': tokens.add(new Token(TokenType.MODULO, "%")); break;
                    case '^': tokens.add(new Token(TokenType.POWER, "^")); break;
                    case '(': tokens.add(new Token(TokenType.LPAREN, "(")); break;
                    case ')': tokens.add(new Token(TokenType.RPAREN, ")")); break;
                    case ',': tokens.add(new Token(TokenType.COMMA, ",")); break;
                    default:
                        throw new IllegalArgumentException("Unexpected character in expression: " + c);
                }
                i++;
            }

            tokens.add(new Token(TokenType.EOF, ""));
            return List.copyOf(tokens);
        }

        double parse() {
            double result = parseExpression();
            if (peek().type != TokenType.EOF) {
                throw new IllegalArgumentException("Unexpected trailing token: " + peek().text);
            }
            return result;
        }

        private Token peek() {
            return tokens.get(pos);
        }

        private void consume() {
            if (pos < tokens.size() - 1) {
                pos++;
            }
        }

        private boolean match(TokenType type) {
            if (peek().type == type) {
                consume();
                return true;
            }
            return false;
        }

        private void expect(String errorMsg) {
            if (peek().type != TokenType.RPAREN) {
                throw new IllegalArgumentException(errorMsg + ", but found: " + peek().text);
            }
            consume();
        }

        // Expression -> Term ((PLUS | MINUS) Term)*
        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                if (match(TokenType.PLUS)) {
                    value += parseTerm();
                } else if (match(TokenType.MINUS)) {
                    value -= parseTerm();
                } else {
                    break;
                }
            }
            return value;
        }

        // Term -> Factor ((MULTIPLY | DIVIDE | MODULO) Factor)*
        private double parseTerm() {
            double value = parsePower();
            while (true) {
                if (match(TokenType.MULTIPLY)) {
                    value *= parsePower();
                } else if (match(TokenType.DIVIDE)) {
                    double divisor = parsePower();
                    if (divisor == 0.0) {
                        throw new ArithmeticException("Division by zero");
                    }
                    value /= divisor;
                } else if (match(TokenType.MODULO)) {
                    double divisor = parsePower();
                    if (divisor == 0.0) {
                        throw new ArithmeticException("Division by zero in modulo");
                    }
                    value %= divisor;
                } else {
                    break;
                }
            }
            return value;
        }

        // Power -> Unary (POWER Power)? (Right-associative)
        private double parsePower() {
            double base = parseUnary();
            if (match(TokenType.POWER)) {
                double exponent = parsePower();
                return Math.pow(base, exponent);
            }
            return base;
        }

        // Unary -> PLUS Unary | MINUS Unary | Primary
        private double parseUnary() {
            if (match(TokenType.PLUS)) {
                return parseUnary();
            }
            if (match(TokenType.MINUS)) {
                return -parseUnary();
            }
            return parsePrimary();
        }

        // Primary -> NUMBER | IDENTIFIER '(' Args ')' | IDENTIFIER | '(' Expression ')'
        private double parsePrimary() {
            Token t = peek();

            if (t.type == TokenType.NUMBER) {
                consume();
                return t.numberValue;
            }

            if (t.type == TokenType.IDENTIFIER) {
                consume();
                String name = t.text;

                if (match(TokenType.LPAREN)) {
                    List<Double> args = new ArrayList<>();
                    if (!match(TokenType.RPAREN)) {
                        do {
                            args.add(parseExpression());
                        } while (match(TokenType.COMMA));
                        expect("Expected ')' after function arguments");
                    }
                    return evaluateFunction(name, args);
                }

                // Variable lookup
                if (variables != null && variables.containsKey(name)) {
                    Double val = variables.get(name);
                    return val != null ? val : 0.0;
                }

                throw new IllegalArgumentException("Unknown variable or function: " + name);
            }

            if (match(TokenType.LPAREN)) {
                double val = parseExpression();
                expect("Expected ')' after expression");
                return val;
            }

            throw new IllegalArgumentException("Expected expression, but got: " + t.text);
        }

        private double evaluateFunction(String name, List<Double> args) {
            String func = name.toLowerCase(Locale.ROOT);
            return switch (func) {
                case "min" -> {
                    if (args.size() != 2) {
                        throw new IllegalArgumentException("min requires 2 arguments, got " + args.size());
                    }
                    yield Math.min(args.getFirst(), args.get(1));
                }
                case "max" -> {
                    if (args.size() != 2) {
                        throw new IllegalArgumentException("max requires 2 arguments, got " + args.size());
                    }
                    yield Math.max(args.getFirst(), args.get(1));
                }
                case "abs" -> {
                    if (args.size() != 1) {
                        throw new IllegalArgumentException("abs requires 1 argument, got " + args.size());
                    }
                    yield Math.abs(args.getFirst());
                }
                case "sqrt" -> {
                    if (args.size() != 1) {
                        throw new IllegalArgumentException("sqrt requires 1 argument, got " + args.size());
                    }
                    yield Math.sqrt(args.getFirst());
                }
                case "ceil" -> {
                    if (args.size() != 1) {
                        throw new IllegalArgumentException("ceil requires 1 argument, got " + args.size());
                    }
                    yield Math.ceil(args.getFirst());
                }
                case "floor" -> {
                    if (args.size() != 1) {
                        throw new IllegalArgumentException("floor requires 1 argument, got " + args.size());
                    }
                    yield Math.floor(args.getFirst());
                }
                case "round" -> {
                    if (args.size() != 1) {
                        throw new IllegalArgumentException("round requires 1 argument, got " + args.size());
                    }
                    yield (double)Math.round(args.getFirst());
                }
                case "sin" -> {
                    if (args.size() != 1) {
                        throw new IllegalArgumentException("sin requires 1 argument, got " + args.size());
                    }
                    yield Math.sin(args.getFirst());
                }
                case "cos" -> {
                    if (args.size() != 1) {
                        throw new IllegalArgumentException("cos requires 1 argument, got " + args.size());
                    }
                    yield Math.cos(args.getFirst());
                }
                case "tan" -> {
                    if (args.size() != 1) {
                        throw new IllegalArgumentException("tan requires 1 argument, got " + args.size());
                    }
                    yield Math.tan(args.getFirst());
                }
                case "random" -> {
                    if (!args.isEmpty()) {
                        throw new IllegalArgumentException("random requires 0 arguments, got " + args.size());
                    }
                    yield Math.random();
                }
                default -> throw new IllegalArgumentException("Unsupported function: " + name);
            };
        }
    }
}
