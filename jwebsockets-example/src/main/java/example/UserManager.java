package example;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum UserManager {

    INSTANCE;

    private final List<User> users = new ArrayList<>();

    UserManager() {
        users.add(new User(1, "Mark", "a"));
        users.add(new User(2, "Bob", "b"));
        users.add(new User(3, "John", "c"));
    }

    /**
     * Finds a user by their authentication token.
     *
     * @param token the authentication token of the user
     * @return the matching user, or an empty optional if no user matches
     */
    public Optional<User> findUserByToken(String token) {
        return token == null ? Optional.empty() : users.stream()
            .filter(user -> user.getToken().equals(token))
            .findFirst();
    }
}
