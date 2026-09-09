package pl.mindupgrade.android;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import java.time.Instant;
public class MorningAlarmTest {
    private void check(String now, String expected) {
        assertEquals(Instant.parse(expected).toEpochMilli(), MorningAlarm.next(Instant.parse(now).toEpochMilli()));
    }
    @Test public void beforeAndAfterMorning() {
        check("2026-09-09T04:00:00Z", "2026-09-09T04:01:00Z");
        check("2026-09-09T04:01:00Z", "2026-09-10T04:01:00Z");
    }
    @Test public void daylightSavingTransitions() {
        check("2026-03-28T06:00:00Z", "2026-03-29T04:01:00Z");
        check("2026-10-24T06:00:00Z", "2026-10-25T05:01:00Z");
    }
}
