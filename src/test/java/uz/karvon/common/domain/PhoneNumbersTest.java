package uz.karvon.common.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"+998901234567", "998901234567", "+998 90 123 45 67", "+998 (90) 123-45-67", " 998901234567 "})
    void acceptsUzbekNumbersInCommonFormats(String raw) {
        assertThat(PhoneNumbers.normalize(raw)).hasValue("+998901234567");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"901234567", "+99890123456", "+9989012345678", "+79001234567", "+998abc234567", "8901234567"})
    void rejectsEverythingElse(String raw) {
        assertThat(PhoneNumbers.normalize(raw)).isEmpty();
    }
}
