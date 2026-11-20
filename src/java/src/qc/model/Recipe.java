package qc.model;


import java.util.List;

public final class Recipe {
    private final String id;
    private final ProductModel model;
    private final String version;
    private final Employee approvedBy;
    private final String approvedAt;
    private final int windowSize;
    private final ResponseAnalyzer analyzer;

    public Recipe(String id, ProductModel model, String version, Employee approvedBy,
                  String approvedAt, int windowSize, ResponseAnalyzer analyzer) {
        if (!approvedBy.canApproveRecipe()) {
            throw new IllegalArgumentException("Методику может утверждать только метролог");
        }
        if (windowSize < 1 || windowSize % 2 == 0) {
            throw new IllegalArgumentException("Размер окна должен быть положительным нечетным числом");
        }
        if (analyzer.getWindowSize() != windowSize) {
            throw new IllegalArgumentException("Анализатор не соответствует размеру окна");
        }
        this.id = id;
        this.model = model;
        this.version = version;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.windowSize = windowSize;
        this.analyzer = analyzer;
    }

    public String getId() { return id; }
    public ProductModel getModel() { return model; }
    public String getVersion() { return version; }
    public Employee getApprovedBy() { return approvedBy; }
    public String getApprovedAt() { return approvedAt; }
    public int getWindowSize() { return windowSize; }

    public AnalysisResult analyze(List<Measurement> points, RecipeLimit limit) {
        if (limit.getRecipe() != this) {
            throw new IllegalArgumentException("Допуск относится к другой версии методики");
        }
        return analyzer.analyze(points, limit);
    }
}
