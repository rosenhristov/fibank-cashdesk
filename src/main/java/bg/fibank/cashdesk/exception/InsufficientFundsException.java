package bg.fibank.cashdesk.exception;

import bg.fibank.cashdesk.model.Currency;

public class InsufficientFundsException extends CashDeskException {

    public InsufficientFundsException(String cashierName, Currency currency,
                                      int faceValue, int requested,
                                      int available) {
        super(String.format(
            "Insufficient funds for cashier '%s': cannot withdraw %d × %d %s (requested %d bill(s), available %d bill(s)).",
            cashierName, requested, faceValue, currency, requested, available)
        );
    }

    @Override
    public int getHttpStatus() {
        return 422;
    }

}
