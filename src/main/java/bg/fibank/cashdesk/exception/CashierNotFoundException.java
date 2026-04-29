package bg.fibank.cashdesk.exception;

public class CashierNotFoundException extends CashDeskException {

    public CashierNotFoundException(String cashierName) {
            super(String.format("Cashier not found: '%s'. Valid cashiers are: MARTINA, PETER, LINDA.", cashierName));
    }

    @Override
    public int getHttpStatus() {
        return 404;
    }

}
