package qc.model;

public final class ProductModel {
    private final String id;
    private final String name;

    public ProductModel(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
