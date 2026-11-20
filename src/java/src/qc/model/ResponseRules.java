package qc.model;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class ResponseRules {
    private ResponseRules() { }

    public static void checkResponse(List<Measurement> points, int windowSize) {
        if (windowSize < 1 || windowSize % 2 == 0) {
            throw new IllegalArgumentException("Размер окна должен быть положительным нечетным числом");
        }
        if (points.size() < (long) windowSize + 2) {
            throw new IllegalArgumentException("После сглаживания должны остаться хотя бы три точки");
        }
        for (Measurement point : points) {
            if (!Double.isFinite(point.getAcceleration() * windowSize)) {
                throw new IllegalArgumentException("Ускорение слишком велико для расчета суммы окна");
            }
        }
        double step = points.get(1).getFrequency() - points.get(0).getFrequency();
        if (step <= 0) {
            throw new IllegalArgumentException("Частоты должны возрастать");
        }
        for (int i = 1; i < points.size(); i++) {
            double difference = points.get(i).getFrequency() - points.get(i - 1).getFrequency();
            if (!isClose(difference, step, 1e-9)) {
                throw new IllegalArgumentException("Нужна равномерная возрастающая сетка частот без повторов");
            }
        }
    }

    public static boolean isClose(double left, double right, double absoluteTolerance) {
        return Math.abs(left - right) <= Math.max(absoluteTolerance,
                1e-9 * Math.max(Math.abs(left), Math.abs(right)));
    }

    public static Instant parseTime(String value) {
        try {
            // Время с пробелом вместо T принимается и в Python.
            String normalized = value.length() > 10 && value.charAt(10) == ' '
                    ? value.substring(0, 10) + "T" + value.substring(11) : value;
            OffsetDateTime time = OffsetDateTime.parse(normalized);
            if (time.getYear() < 1 || time.getYear() > 9999) {
                throw new IllegalArgumentException("Год проверки должен быть от 1 до 9999");
            }
            return time.toInstant().truncatedTo(ChronoUnit.MICROS);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Нужно время ISO 8601 с часовым поясом", error);
        }
    }

    public static int compareText(String left, String right) {
        // Сравниваем символы Unicode так же, как Python.
        var a = left.codePoints().iterator();
        var b = right.codePoints().iterator();
        while (a.hasNext() && b.hasNext()) {
            int difference = Integer.compare(a.nextInt(), b.nextInt());
            if (difference != 0) {
                return difference;
            }
        }
        return Boolean.compare(a.hasNext(), b.hasNext());
    }
}
