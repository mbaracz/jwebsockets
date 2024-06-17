package example;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Singleton class that manages a list of users.
 */
public enum UserManager {

    INSTANCE;

    private final List<User> users = new ArrayList<>();

    /**
     * Initializes the UserManager with a predefined list of users.
     */
    UserManager() {
        users.add(new User(1, "Mark", "a"));
        users.add(new User(2, "Bob", "b"));
        users.add(new User(3, "John", "c"));
    }

    /**
     * Finds a user by their authentication token.
     *
     * @param token the authentication token of the user
     * @return an Optional containing the user if found, or an empty Optional if not found or if the token is null
     */
    public Optional<User> findUserByToken(String token) {
        return token == null ? Optional.empty() : users.stream()
                .filter(user -> user.getToken().equals(token))
                .findFirst();
    }
}
