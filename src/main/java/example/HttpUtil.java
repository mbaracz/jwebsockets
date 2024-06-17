package example;

import java.util.List;

/**
 * A utility class for HTTP-related functionalities.
 */
public class HttpUtil {

    /**
     * Finds the value of a specified cookie from a list of cookies.
     *
     * @param cookies    the list of cookies in the format "name=value"
     * @param cookieName the name of the cookie to find
     * @return the value of the specified cookie, or {@code null} if not found
     */
    public static String findCookieValue(List<String> cookies, String cookieName) {
        return cookies.stream()
                .map(cookie -> cookie.split("=", 2))
                .filter(parts -> parts.length == 2 && parts[0].trim().equals(cookieName))
                .findFirst()
                .map(parts -> parts[1].trim())
                .orElse(null);
    }
}
