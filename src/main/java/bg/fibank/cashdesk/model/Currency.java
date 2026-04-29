package bg.fibank.cashdesk.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Currency {

    BGN("Bulgarian Lev"),
    EUR("Euro");

    private final String value;

}
