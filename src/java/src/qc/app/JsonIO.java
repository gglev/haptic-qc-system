package qc.app;

import qc.model.ProductModel;
import qc.model.Lot;
import qc.model.Product;
import qc.model.Employee;
import qc.model.Metrolog;
import qc.model.Recipe;
import qc.model.ResponseAnalyzer;
import qc.analysis.RawResponseAnalyzer;
import qc.analysis.MovingAverageAnalyzer;
import qc.model.Parameter;
import qc.model.RecipeLimit;
import qc.model.TestRun;
import qc.model.Measurement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JsonIO {
    private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting()
            .disableHtmlEscaping().create();

    private JsonIO() { }

    public static Batch loadBatch(Path path) throws IOException {
        JsonObject data;
        try (Reader file = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(file)) {
            reader.setStrictness(Strictness.STRICT);
            data = object(JsonParser.parseReader(reader));
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("После JSON есть лишние данные");
            }
        }
        Map<String, JsonObject> modelRows = index(data, "product_models", "model_id");
        Map<String, JsonObject> lotRows = index(data, "lots", "lot_id");
        Map<String, JsonObject> productRows = index(data, "products", "product_id");
        Map<String, JsonObject> employeeRows = index(data, "employees", "employee_id");
        Map<String, JsonObject> recipeRows = index(data, "recipes", "recipe_id");
        Map<String, JsonObject> parameterRows = index(data, "parameters", "parameter_id");
        Map<String, JsonObject> runRows = index(data, "test_runs", "run_id");
        unique(modelRows, "name");
        unique(lotRows, "lot_number");
        unique(productRows, "serial_number");
        unique(employeeRows, "login");
        unique(parameterRows, "code");
        unique(recipeRows, "model_id", "version");

        Map<String, ProductModel> models = new LinkedHashMap<>();
        for (var entry : modelRows.entrySet()) {
            models.put(entry.getKey(), new ProductModel(entry.getKey(), text(entry.getValue(), "name")));
        }
        Map<String, Lot> lots = new LinkedHashMap<>();
        for (var entry : lotRows.entrySet()) {
            JsonObject row = entry.getValue();
            lots.put(entry.getKey(), new Lot(entry.getKey(), reference(models, row, "model_id"), text(row, "lot_number")));
        }
        Map<String, Product> products = new LinkedHashMap<>();
        for (var entry : productRows.entrySet()) {
            JsonObject row = entry.getValue();
            products.put(entry.getKey(), new Product(entry.getKey(), reference(lots, row, "lot_id"), text(row, "serial_number")));
        }
        Set<String> approvers = new HashSet<>();
        for (JsonObject row : recipeRows.values()) {
            approvers.add(text(row, "approved_by"));
        }
        Map<String, Employee> employees = new LinkedHashMap<>();
        for (var entry : employeeRows.entrySet()) {
            String id = entry.getKey();
            String login = text(entry.getValue(), "login");
            String fullName = text(entry.getValue(), "full_name");
            employees.put(id, approvers.contains(id) ? new Metrolog(id, login, fullName)
                    : new Employee(id, login, fullName));
        }
        Map<String, Recipe> recipes = new LinkedHashMap<>();
        for (var entry : recipeRows.entrySet()) {
            JsonObject row = entry.getValue();
            int window;
            try {
                window = integer(row, "window_size").intValueExact();
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("Размер окна слишком велик", error);
            }
            ResponseAnalyzer analyzer = window == 1 ? new RawResponseAnalyzer()
                    : new MovingAverageAnalyzer(window);
            recipes.put(entry.getKey(), new Recipe(entry.getKey(), reference(models, row, "model_id"),
                    text(row, "version"), reference(employees, row, "approved_by"), text(row, "approved_at"), window, analyzer));
        }
        Map<String, Parameter> parameters = new LinkedHashMap<>();
        for (var entry : parameterRows.entrySet()) {
            JsonObject row = entry.getValue();
            parameters.put(entry.getKey(), new Parameter(entry.getKey(), text(row, "code"), text(row, "name"), text(row, "unit")));
        }
        Map<List<String>, RecipeLimit> limits = new LinkedHashMap<>();
        for (JsonElement item : array(data, "recipe_limits")) {
            JsonObject row = object(item);
            Recipe recipe = reference(recipes, row, "recipe_id");
            Parameter parameter = reference(parameters, row, "parameter_id");
            List<String> key = List.of(recipe.getId(), parameter.getId());
            if (limits.containsKey(key)) {
                throw new IllegalArgumentException("Повтор допуска");
            }
            limits.put(key, new RecipeLimit(recipe, parameter, number(row, "lower_limit"), number(row, "upper_limit")));
        }
        Map<String, List<Measurement>> points = new LinkedHashMap<>();
        Map<String, Set<BigInteger>> pointNumbers = new LinkedHashMap<>();
        for (String id : runRows.keySet()) {
            points.put(id, new ArrayList<>());
            pointNumbers.put(id, new HashSet<>());
        }
        for (JsonElement item : array(data, "measurements")) {
            JsonObject row = object(item);
            String runId = text(row, "run_id");
            if (!points.containsKey(runId)) {
                throw new IllegalArgumentException("Не найдена проверка для измерения");
            }
            BigInteger pointNo = integer(row, "point_no");
            if (!pointNumbers.get(runId).add(pointNo)) {
                throw new IllegalArgumentException("Повтор номера точки в проверке");
            }
            points.get(runId).add(new Measurement(pointNo, number(row, "frequency_hz"), number(row, "acceleration_m_s2")));
        }
        List<TestRun> runs = new ArrayList<>();
        Set<String> batchLots = new HashSet<>();
        for (var entry : runRows.entrySet()) {
            JsonObject row = entry.getValue();
            if (!text(row, "status").equals("NEW")) {
                throw new IllegalArgumentException("На вход принимаются только новые проверки со статусом NEW");
            }
            Product product = reference(products, row, "product_id");
            Recipe recipe = reference(recipes, row, "recipe_id");
            Parameter frequency = null;
            for (Parameter parameter : parameters.values()) {
                if (parameter.getCode().equals("peak_frequency_hz") && parameter.getUnit().equals("Гц")) {
                    frequency = parameter;
                }
            }
            if (frequency == null) {
                throw new IllegalArgumentException("Нужен параметр peak_frequency_hz с единицей Гц");
            }
            RecipeLimit limit = limits.get(List.of(recipe.getId(), frequency.getId()));
            if (limit == null) {
                throw new IllegalArgumentException("Для методики не задан допуск частоты");
            }
            List<Measurement> ordered = points.get(entry.getKey());
            ordered.sort(Comparator.comparing(Measurement::getPointNo));
            runs.add(new TestRun(entry.getKey(), product, recipe, reference(employees, row, "operator_id"),
                    text(row, "started_at"), limit, ordered));
            batchLots.add(product.getLot().getId());
        }
        if (batchLots.size() > 1) {
            throw new IllegalArgumentException("Один запуск обрабатывает проверки одной партии");
        }
        return new Batch(models, lots, products, employees, recipes, parameters,
                new ArrayList<>(limits.values()), runs);
    }

    public static void saveProtocol(Map<String, Object> result, Path path) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Path temporary = Files.createTempFile(target.getParent(), ".protocol-", ".json");
        try {
            Files.writeString(temporary, GSON.toJson(result) + "\n", StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static JsonObject object(JsonElement item) {
        if (item == null || !item.isJsonObject()) {
            throw new IllegalArgumentException("Нужен объект JSON");
        }
        return item.getAsJsonObject();
    }

    private static JsonArray array(JsonObject data, String name) {
        JsonElement item = data.get(name);
        if (item == null || !item.isJsonArray()) {
            throw new IllegalArgumentException(name + ": нужен список записей");
        }
        return item.getAsJsonArray();
    }

    private static String text(JsonObject row, String name) {
        JsonElement value = row.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(name + ": нужна непустая строка");
        }
        return value.getAsString();
    }

    private static double number(JsonObject row, String name) {
        JsonElement value = row.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(value.getAsDouble())) {
            throw new IllegalArgumentException(name + ": нужно конечное число");
        }
        return value.getAsDouble();
    }

    private static BigInteger integer(JsonObject row, String name) {
        JsonElement value = row.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException(name + ": нужно целое число");
        }
        return new BigInteger(value.getAsString());
    }

    private static Map<String, JsonObject> index(JsonObject data, String table, String key) {
        Map<String, JsonObject> rows = new LinkedHashMap<>();
        for (JsonElement item : array(data, table)) {
            JsonObject row = object(item);
            String id = text(row, key);
            if (rows.putIfAbsent(id, row) != null) {
                throw new IllegalArgumentException(table + ": повтор ключа " + id);
            }
        }
        return rows;
    }

    private static void unique(Map<String, JsonObject> rows, String... fields) {
        Set<List<String>> seen = new HashSet<>();
        for (JsonObject row : rows.values()) {
            List<String> key = new ArrayList<>();
            for (String field : fields) {
                key.add(text(row, field));
            }
            if (!seen.add(key)) {
                throw new IllegalArgumentException("Повтор значений: " + String.join(", ", fields));
            }
        }
    }

    private static <T> T reference(Map<String, T> index, JsonObject row, String field) {
        T record = index.get(text(row, field));
        if (record == null) {
            throw new IllegalArgumentException("Не найдена связанная запись " + field);
        }
        return record;
    }
}
