package qc.model;

public final class Product {
    private final String id;
    private final Lot lot;
    private final String serialNumber;

    public Product(String id, Lot lot, String serialNumber) {
        this.id = id;
        this.lot = lot;
        this.serialNumber = serialNumber;
    }

    public String getId() {
        return id;
    }

    public Lot getLot() {
        return lot;
    }

    public String getSerialNumber() {
        return serialNumber;
    }
}
