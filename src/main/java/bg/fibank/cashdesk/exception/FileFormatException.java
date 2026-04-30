package bg.fibank.cashdesk.exception;

public class FileFormatException extends CashDeskException {

    public FileFormatException(String message) {
        super(message);
    }

    public FileFormatException(String message, Throwable cause) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 500;
    }
}