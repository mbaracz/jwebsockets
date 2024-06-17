package example;

/**
 * A class representing a user in the system.
 */
public class User {

    private final long id;
    private final String name;
    private String token;

    /**
     * Constructs a new User with the specified id, name, and token.
     *
     * @param id    the unique identifier of the user
     * @param name  the name of the user
     * @param token the authentication token of the user
     */
    public User(long id, String name, String token) {
        this.id = id;
        this.name = name;
        this.token = token;
    }

    /**
     * Gets the unique identifier of the user.
     *
     * @return the user ID
     */
    public long getId() {
        return id;
    }

    /**
     * Gets the name of the user.
     *
     * @return the user name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the authentication token of the user.
     *
     * @return the user token
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets a new authentication token for the user.
     *
     * @param token the new token
     */
    public void setToken(String token) {
        this.token = token;
    }
}
