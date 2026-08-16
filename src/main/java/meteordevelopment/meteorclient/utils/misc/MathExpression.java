/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.utils.misc;

public final class MathExpression {
    private MathExpression() {
    }

    public static Double parseDouble(String value) {
        try {
            Parser parser = new Parser(value);
            double result = parser.expression();
            parser.skipWhitespace();
            return parser.atEnd() && Double.isFinite(result) ? result : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static Integer parseInt(String value) {
        Double result = parseDouble(value);
        if (result == null || result < Integer.MIN_VALUE || result > Integer.MAX_VALUE || result != Math.rint(result)) return null;
        return result.intValue();
    }

    private static class Parser {
        private final String value;
        private int index;

        private Parser(String value) {
            this.value = value;
        }

        private double expression() {
            double result = term();
            while (true) {
                if (consume('+')) result += term();
                else if (consume('-')) result -= term();
                else return result;
            }
        }

        private double term() {
            double result = factor();
            while (true) {
                if (consume('*')) result *= factor();
                else if (consume('/')) {
                    double divisor = factor();
                    if (divisor == 0) throw new IllegalArgumentException();
                    result /= divisor;
                } else return result;
            }
        }

        private double factor() {
            if (consume('+')) return factor();
            if (consume('-')) return -factor();
            if (consume('(')) {
                double result = expression();
                if (!consume(')')) throw new IllegalArgumentException();
                return result;
            }

            skipWhitespace();
            int start = index;
            boolean decimal = false;
            while (index < value.length()) {
                char character = value.charAt(index);
                if (Character.isDigit(character)) index++;
                else if (character == '.' && !decimal) {
                    decimal = true;
                    index++;
                } else break;
            }
            if (start == index || (decimal && start + 1 == index)) throw new IllegalArgumentException();

            return Double.parseDouble(value.substring(start, index));
        }

        private boolean consume(char character) {
            skipWhitespace();
            if (index >= value.length() || value.charAt(index) != character) return false;
            index++;
            return true;
        }

        private void skipWhitespace() {
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        }

        private boolean atEnd() {
            return index >= value.length();
        }
    }
}
