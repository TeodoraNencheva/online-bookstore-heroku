package bg.softuni.onlinebookstore.model.error;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
    }

    public InvalidTokenException(String message) {
        super(message);
    }
}
