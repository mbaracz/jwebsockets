package example;

public class User {

    private final long id;
    private final String name;
    private String token;

    public User(long id, String name, String token) {
        this.id = id;
        this.name = name;
        this.token = token;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
