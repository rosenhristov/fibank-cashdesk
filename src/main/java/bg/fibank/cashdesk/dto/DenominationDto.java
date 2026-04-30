package bg.fibank.cashdesk.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DenominationDto {

    @Positive(message = "Face value must be positive")
    private int faceValue;

    @Positive(message = "Count must be positive")
    private int count;

}
