package qc.model;

public final class Lot {
    private final String id;
    private final ProductModel model;
    private final String number;

    public Lot(String id, ProductModel model, String number) {
        this.id = id;
        this.model = model;
        this.number = number;
    }

    public String getId() {
        return id;
    }

    public ProductModel getModel() {
        return model;
    }

    public String getNumber() {
        return number;
    }
}
