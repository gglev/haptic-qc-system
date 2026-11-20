package qc.model;

public final class RecipeLimit {
    private final Recipe recipe;
    private final Parameter parameter;
    private final double lower;
    private final double upper;

    public RecipeLimit(Recipe recipe, Parameter parameter, double lower, double upper) {
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower <= 0 || lower > upper) {
            throw new IllegalArgumentException("Неверные границы допуска частоты");
        }
        this.recipe = recipe;
        this.parameter = parameter;
        this.lower = lower;
        this.upper = upper;
    }

    public Recipe getRecipe() { return recipe; }
    public Parameter getParameter() { return parameter; }
    public double getLower() { return lower; }
    public double getUpper() { return upper; }

    public boolean contains(double frequency) {
        return lower <= frequency && frequency <= upper;
    }
}
