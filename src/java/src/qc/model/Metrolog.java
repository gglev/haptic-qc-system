package qc.model;

public final class Metrolog extends Employee {
    public Metrolog(String id, String login, String fullName) {
        super(id, login, fullName);
    }

    @Override
    public boolean canApproveRecipe() {
        return true;
    }
}
