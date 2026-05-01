package bg.fibank.cashdesk.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * API representation of a single denomination slot.
 *
 * <p>Used in both directions — incoming operation requests carry the
 * denomination breakdown to deposit or withdraw, and outgoing balance
 * responses carry the live denomination state of the till.</p>
 *
 * @param faceValue face value of the bill (e.g. 10, 20, 50)
 * @param count     number of bills of this face value
 */
public record DenominationDto(

        @Positive(message = "faceValue must be a positive integer")
        int faceValue,

        @Min(value = 1, message = "count must be at least 1")
        int count
) {}

