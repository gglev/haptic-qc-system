package qc.model;

public class Employee {
    private final String id;
    private final String login;
    private final String fullName;

    public Employee(String id, String login, String fullName) {
        this.id = id;
        this.login = login;
        this.fullName = fullName;
    }

    public String getId() { return id; }
    public String getLogin() { return login; }
    public String getFullName() { return fullName; }

    public boolean canApproveRecipe() {
        return false;
    }
}
