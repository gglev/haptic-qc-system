package qc.analysis;

import qc.model.RecipeLimit;
import qc.model.Measurement;
import qc.model.ResponseAnalyzer;
import qc.model.AnalysisResult;
import qc.model.ResponseRules;

import java.util.ArrayList;
import java.util.List;

public final class RawResponseAnalyzer implements ResponseAnalyzer {
    @Override
    public int getWindowSize() { return 1; }

    @Override
    public AnalysisResult analyze(List<Measurement> points, RecipeLimit limit) {
        ResponseRules.checkResponse(points, 1);
        List<Double> frequencies = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (Measurement point : points) {
            frequencies.add(point.getFrequency());
            values.add(point.getAcceleration());
        }
        return AnalysisResult.evaluate(frequencies, values, limit);
    }
}
