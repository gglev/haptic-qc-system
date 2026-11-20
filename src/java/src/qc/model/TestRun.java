package qc.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TestRun {
    private final String id;
    private final Product product;
    private final Recipe recipe;
    private final Employee operator;
    private final String startedAt;
    private final Instant startedInstant;
    private final RecipeLimit limit;
    private final List<Measurement> measurements;
    private String status;
    private AnalysisResult result;

    public TestRun(String id, Product product, Recipe recipe, Employee operator,
                   String startedAt, RecipeLimit limit, List<Measurement> measurements) {
        if (product.getLot().getModel() != recipe.getModel()) {
            throw new IllegalArgumentException("Методика не соответствует модели изделия");
        }
        if (limit.getRecipe() != recipe) {
            throw new IllegalArgumentException("Для методики не задан допуск частоты");
        }
        ResponseRules.checkResponse(measurements, recipe.getWindowSize());
        this.id = id;
        this.product = product;
        this.recipe = recipe;
        this.operator = operator;
        this.startedAt = startedAt;
        this.startedInstant = ResponseRules.parseTime(startedAt);
        this.limit = limit;
        this.measurements = List.copyOf(measurements);
        this.status = "NEW";
    }

    public String getId() { return id; }
    public Product getProduct() { return product; }
    public Recipe getRecipe() { return recipe; }
    public Employee getOperator() { return operator; }
    public String getStartedAt() { return startedAt; }
    public Instant getStartedInstant() { return startedInstant; }
    public String getStatus() { return status; }
    public List<Measurement> getMeasurements() { return measurements; }

    public AnalysisResult analyze() {
        result = recipe.analyze(measurements, limit);
        status = result.getStatus();
        return result;
    }

    public Map<String, Object> toProtocol() {
        if (result == null) {
            throw new IllegalStateException("Проверка еще не рассчитана");
        }
        Map<String, Object> record = result.toMap();
        record.put("run_id", id);
        record.put("product_id", product.getId());
        record.put("started_at", startedAt);
        record.put("serial_number", product.getSerialNumber());
        record.put("recipe_id", recipe.getId());
        record.put("lower_limit", limit.getLower());
        record.put("upper_limit", limit.getUpper());
        return record;
    }
}
