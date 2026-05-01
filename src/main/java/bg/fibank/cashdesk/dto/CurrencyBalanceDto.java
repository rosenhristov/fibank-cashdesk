package bg.fibank.cashdesk.dto;

import java.util.List;

/**
 * The live balance for a single currency within one cashier's till.
 *
 * @param total         sum of all {@code faceValue × count} entries
 * @param denominations denomination slots sorted ascending by face value
 */
public record CurrencyBalanceDto(

        int total,
        List<DenominationDto> denominations) {

}

