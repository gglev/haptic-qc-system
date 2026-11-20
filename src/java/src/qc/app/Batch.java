package qc.app;

import qc.model.ProductModel;
import qc.model.Lot;
import qc.model.Product;
import qc.model.Employee;
import qc.model.Recipe;
import qc.model.Parameter;
import qc.model.RecipeLimit;
import qc.model.TestRun;
import qc.analysis.BatchSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Batch {
    private final Map<String, ProductModel> models;
    private final Map<String, Lot> lots;
    private final Map<String, Product> products;
    private final Map<String, Employee> employees;
    private final Map<String, Recipe> recipes;
    private final Map<String, Parameter> parameters;
    private final List<RecipeLimit> limits;
    private final List<TestRun> runs;

    public Batch(Map<String, ProductModel> models, Map<String, Lot> lots,
                 Map<String, Product> products, Map<String, Employee> employees,
                 Map<String, Recipe> recipes, Map<String, Parameter> parameters,
                 List<RecipeLimit> limits, List<TestRun> runs) {
        this.models = Collections.unmodifiableMap(new LinkedHashMap<>(models));
        this.lots = Collections.unmodifiableMap(new LinkedHashMap<>(lots));
        this.products = Collections.unmodifiableMap(new LinkedHashMap<>(products));
        this.employees = Collections.unmodifiableMap(new LinkedHashMap<>(employees));
        this.recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        this.limits = List.copyOf(limits);
        this.runs = List.copyOf(runs);
    }

    public List<TestRun> getRuns() { return runs; }

    public Map<String, Object> analyze() {
        List<Map<String, Object>> protocols = new ArrayList<>();
        for (TestRun run : runs) {
            run.analyze();
            protocols.add(run.toProtocol());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("test_runs", protocols);
        result.put("summary", new BatchSummary(runs).toMap());
        return result;
    }
}
