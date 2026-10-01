import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AirlockTest {
    @ParameterizedTest(name = "pressure={0}, sealed={1}, mayOpen={2}")
    @CsvSource({
            "0, true, true",
            "5, true, true",
            "6, true, false",
            "101, true, false",
            "0, false, false",
            "101, false, false",
            "-1, true, false"
    })
    void checksBothInterlocks(int pressureKpa, boolean innerDoorSealed, boolean expected) {
        assertEquals(expected, Airlock.canOpen(pressureKpa, innerDoorSealed));
    }
}
