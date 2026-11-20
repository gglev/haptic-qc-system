package qc;

import qc.model.*;
import qc.analysis.*;
import qc.app.*;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Checks {
    private static int passed;

    private Checks() { }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError(name);
        }
        passed++;
    }

    private static JsonObject input(String name) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("examples", name))).getAsJsonObject();
    }

    private static JsonObject calculate(JsonObject data, Path directory) throws Exception {
        Path source = directory.resolve("input.json");
        Files.writeString(source, data.toString(), StandardCharsets.UTF_8);
        return new GsonBuilder().serializeNulls().create().toJsonTree(JsonIO.loadBatch(source).analyze()).getAsJsonObject();
    }

    private static void expectFailure(JsonObject data, Path directory, String name) throws Exception {
        Path source = directory.resolve("bad.json");
        Path target = directory.resolve("old.json");
        Files.writeString(source, data.toString(), StandardCharsets.UTF_8);
        Files.writeString(target, "old protocol", StandardCharsets.UTF_8);
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) {
            check(Main.run(new String[] {source.toString(), target.toString()}, output) == 1, name);
        }
        check(Files.readString(target).equals("old protocol"), name + ": сохранен старый протокол");
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("qc-checks-");
        try {
            for (String[] pair : List.of(new String[] {"batch.json", "expected_result.json"},
                    new String[] {"repeated_batch.json", "repeated_expected.json"})) {
                JsonObject actual = calculate(input(pair[0]), directory);
                check(actual.equals(input(pair[1])), "Ожидаемый результат " + pair[0]);
            }
            JsonObject data = input("batch.json");
            JsonArray points = data.getAsJsonArray("measurements");
            JsonArray reverse = new JsonArray();
            for (int i = points.size() - 1; i >= 0; i--) {
                reverse.add(points.get(i));
            }
            data.add("measurements", reverse);
            check(calculate(data, directory).equals(input("expected_result.json")), "Порядок точек");

            data = input("repeated_batch.json");
            JsonArray runs = data.getAsJsonArray("test_runs");
            runs.get(1).getAsJsonObject().addProperty("started_at", "2026-11-19T08:00:00Z");
            runs.get(3).getAsJsonObject().addProperty("started_at", "2026-11-19T10:00:00+03:00");
            check(calculate(data, directory).getAsJsonObject("summary").get("final_failed").getAsInt() == 1,
                    "Время сравнивается по UTC, последним остался FAIL");
            runs.get(1).getAsJsonObject().addProperty("started_at", "2026-11-19T07:00:00Z");
            JsonObject summary = calculate(data, directory).getAsJsonObject("summary");
            check(summary.get("final_failed").getAsInt() == 0, "При равном времени выбран больший run_id");
            JsonArray reversedRuns = new JsonArray();
            for (int i = runs.size() - 1; i >= 0; i--) {
                reversedRuns.add(runs.get(i));
            }
            data.add("test_runs", reversedRuns);
            check(calculate(data, directory).get("summary").equals(summary), "Порядок проверок не меняет сводку");
            check(ResponseRules.parseTime("2026-11-19T07:00:00.123456789Z")
                    .equals(ResponseRules.parseTime("2026-11-19T07:00:00.123456Z")), "Точность времени как в Python");
            check(ResponseRules.compareText("\uE000", "\uD83D\uDE00") < 0, "Сравнение кодовых точек Unicode");

            data = input("batch.json");
            data.add("test_runs", new JsonArray());
            data.add("measurements", new JsonArray());
            summary = calculate(data, directory).getAsJsonObject("summary");
            check(summary.get("total").getAsInt() == 0 && summary.get("pass_percent").isJsonNull()
                    && summary.get("final_pass_percent").isJsonNull(), "Пустая партия");

            ProductModel model = new ProductModel("M", "Модель");
            Employee operator = new Employee("E1", "operator", "Оператор");
            Employee metrolog = new Metrolog("E2", "metrolog", "Метролог");
            check(!operator.canApproveRecipe() && metrolog.canApproveRecipe(), "Переопределение поведения");
            boolean denied = false;
            try {
                new Recipe("R", model, "1", operator, "2026-11-19", 1, new RawResponseAnalyzer());
            } catch (IllegalArgumentException error) {
                denied = true;
            }
            check(denied, "Оператор не утверждает методику");
            Parameter parameter = new Parameter("F", "peak_frequency_hz", "Частота", "Гц");
            List<Measurement> response = new ArrayList<>();
            double[] accelerations = {0, 0, 8, 0, 5, 5, 0};
            for (int i = 0; i < accelerations.length; i++) {
                response.add(new Measurement(BigInteger.valueOf(i + 1), 150 + 10 * i, accelerations[i]));
            }
            Recipe raw = new Recipe("RAW", model, "1", metrolog, "2026-11-19", 1, new RawResponseAnalyzer());
            Recipe smooth = new Recipe("AVG", model, "2", metrolog, "2026-11-19", 3, new MovingAverageAnalyzer(3));
            RecipeLimit rawLimit = new RecipeLimit(raw, parameter, 150, 210);
            RecipeLimit smoothLimit = new RecipeLimit(smooth, parameter, 150, 210);
            check(raw.analyze(response, rawLimit).getPeakFrequency() == 170,
                    "Реализация интерфейса без сглаживания");
            check(smooth.analyze(response, smoothLimit).getPeakFrequency() == 180,
                    "Реализация интерфейса со сглаживанием");
            RecipeLimit lowerBoundary = new RecipeLimit(raw, parameter, 170, 170);
            check(raw.analyze(response, lowerBoundary).getStatus().equals("PASS"), "Граница допуска включена");

            data = input("batch.json");
            data.getAsJsonArray("products").get(0).getAsJsonObject().addProperty("lot_id", "missing");
            expectFailure(data, directory, "Потерянная ссылка");
            data = input("batch.json");
            data.getAsJsonArray("test_runs").get(0).getAsJsonObject().addProperty("started_at", "2026-11-19T10:00:00");
            expectFailure(data, directory, "Время без часового пояса");
            data = input("batch.json");
            data.getAsJsonArray("recipes").get(0).getAsJsonObject().addProperty("window_size", 2);
            expectFailure(data, directory, "Четное окно");
            data = input("batch.json");
            data.getAsJsonArray("measurements").get(0).getAsJsonObject().addProperty("acceleration_m_s2", -1);
            expectFailure(data, directory, "Отрицательное ускорение");
            data = input("batch.json");
            data.getAsJsonArray("measurements").get(0).getAsJsonObject().addProperty("frequency_hz", "150");
            expectFailure(data, directory, "Строка вместо числа");
            data = input("batch.json");
            data.getAsJsonArray("measurements").get(0).getAsJsonObject().addProperty("frequency_hz", 151);
            expectFailure(data, directory, "Неравномерная сетка");
            data = input("batch.json");
            data.getAsJsonArray("products").add(data.getAsJsonArray("products").get(0).deepCopy());
            expectFailure(data, directory, "Повтор первичного ключа");
            data = input("batch.json");
            data.getAsJsonArray("measurements").add(data.getAsJsonArray("measurements").get(0).deepCopy());
            expectFailure(data, directory, "Повтор составного ключа");
            Path source = directory.resolve("same.json");
            String original = input("batch.json").toString();
            Files.writeString(source, original);
            try (PrintStream output = new PrintStream(new ByteArrayOutputStream())) {
                check(Main.run(new String[] {source.toString(), source.toString()}, output) == 1, "Вход не перезаписывается");
            }
            check(Files.readString(source).equals(original), "Входной файл сохранен");

            System.out.println("Пройдено проверок: " + passed);
            System.out.println("Проверены оба примера, два анализатора, наследование, повторный контроль и ошибки входа");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }
}
