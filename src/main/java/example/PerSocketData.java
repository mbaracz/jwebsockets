package example;

/**
 * Represents data associated with a WebSocket session.
 * This class encapsulates the identifier and name of a user connected via WebSocket.
 */
public class PerSocketData {

    private final long id;
    private final String name;

    /**
     * Constructs a new PerSocketData object with the specified identifier and name.
     *
     * @param id   The unique identifier of the user.
     * @param name The name of the user.
     */
    public PerSocketData(long id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Retrieves the unique identifier of the user.
     *
     * @return The user's identifier.
     */
    public long getId() {
        return id;
    }

    /**
     * Retrieves the name of the user.
     *
     * @return The user's name.
     */
    public String getName() {
        return name;
    }
}