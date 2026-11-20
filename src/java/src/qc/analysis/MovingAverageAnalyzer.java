package qc.analysis;

import qc.model.RecipeLimit;
import qc.model.Measurement;
import qc.model.ResponseAnalyzer;
import qc.model.AnalysisResult;
import qc.model.ResponseRules;

import java.util.ArrayList;
import java.util.List;

public final class MovingAverageAnalyzer implements ResponseAnalyzer {
    private final int windowSize;

    public MovingAverageAnalyzer(int windowSize) {
        if (windowSize <= 1 || windowSize % 2 == 0) {
            throw new IllegalArgumentException("Для сглаживания нужно нечетное окно больше 1");
        }
        this.windowSize = windowSize;
    }

    @Override
    public int getWindowSize() { return windowSize; }

    @Override
    public AnalysisResult analyze(List<Measurement> points, RecipeLimit limit) {
        ResponseRules.checkResponse(points, windowSize);
        List<Double> frequencies = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        int half = windowSize / 2;
        for (int start = 0; start <= points.size() - windowSize; start++) {
            double total = 0;
            for (int offset = 0; offset < windowSize; offset++) {
                total += points.get(start + offset).getAcceleration();
            }
            frequencies.add(points.get(start + half).getFrequency());
            values.add(total / windowSize);
        }
        return AnalysisResult.evaluate(frequencies, values, limit);
    }
}
