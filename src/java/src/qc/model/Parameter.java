package qc.model;

public final class Parameter {
    private final String id;
    private final String code;
    private final String name;
    private final String unit;

    public Parameter(String id, String code, String name, String unit) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.unit = unit;
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }
}
