package bg.fibank.cashdesk.exception;

import bg.fibank.cashdesk.model.Currency;

public class DenominationNotFoundException extends CashDeskException {

    public DenominationNotFoundException(String cashierName, Currency currency, int faceValue) {
        super(String.format("Cashier '%s' has no %d %s denomination in their till.",
                            cashierName, faceValue, currency)
        );
    }

    @Override
    public int getHttpStatus() {
        return 422;
    }
}
