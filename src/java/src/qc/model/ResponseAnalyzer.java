package qc.model;

import java.util.List;

public interface ResponseAnalyzer {
    int getWindowSize();
    AnalysisResult analyze(List<Measurement> points, RecipeLimit limit);
}
