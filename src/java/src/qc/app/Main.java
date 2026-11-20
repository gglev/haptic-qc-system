package qc.app;

import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class Main {
    private Main() { }

    public static int run(String[] args, PrintStream output) {
        if (args.length != 0 && args.length != 2) {
            output.println("Запуск: sh run.sh [партия.json результат.json]");
            return 1;
        }
        try {
            Path source = Path.of(args.length == 0 ? "examples/batch.json" : args[0]);
            Path target = Path.of(args.length == 0 ? "result.json" : args[1]);
            Path sourceReal = source.toRealPath();
            Path targetReal = Files.exists(target) ? target.toRealPath()
                    : target.toAbsolutePath().normalize().getParent().toRealPath().resolve(target.getFileName());
            if (sourceReal.equals(targetReal) || (Files.exists(target) && Files.isSameFile(source, target))) {
                throw new IllegalArgumentException("Нельзя записывать результат поверх входного файла");
            }
            Map<String, Object> protocol = JsonIO.loadBatch(source).analyze();
            JsonIO.saveProtocol(protocol, target);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> runs = (List<Map<String, Object>>) protocol.get("test_runs");
            for (Map<String, Object> run : runs) {
                output.println(run.get("serial_number") + ": " + run.get("status")
                        + "; частота: " + run.get("peak_frequency_hz"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) protocol.get("summary");
            output.println("Всего: " + summary.get("total") + "; годных: " + summary.get("passed")
                    + "; не годных: " + summary.get("failed") + "; на повторную проверку: " + summary.get("review"));
            output.println("Уникальных изделий: " + summary.get("unique_products")
                    + "; повторных проверок: " + summary.get("repeated_checks"));
            output.println("Последние проверки: PASS=" + summary.get("final_passed")
                    + "; FAIL=" + summary.get("final_failed") + "; REVIEW=" + summary.get("final_review"));
            output.println("Протокол сохранен: " + target);
            return 0;
        } catch (IOException | IllegalArgumentException | JsonParseException error) {
            output.println("Ошибка обработки: " + error.getMessage());
            return 1;
        }
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }
}
