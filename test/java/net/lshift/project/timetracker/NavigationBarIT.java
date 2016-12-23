package net.lshift.project.timetracker;

import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Interval;
import org.joda.time.LocalTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.openqa.selenium.logging.Logs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static net.lshift.project.timetracker.Constants.*;
import static net.lshift.project.timetracker.Constants.A_USER;
import static net.lshift.project.timetracker.Constants.SEED_DATE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class NavigationBarIT {
    final static Logger log = LoggerFactory.getLogger(TimeSheetScreenIT.class);
    public static final String PICK_USER = "Pick user";
    public static final String TIME_SHEETS = "Time sheets";

    @Rule
    public TestName testName = new TestName();

    private static final int RETRIES = 5;

    private final ServerApi server = new ServerApi();

    private AppDriver driver;

    @Before
    public void forceReloadPageInMostHorribleFashion()
    {
        driver = AppDriver.get();
        driver.forceReload();

    }

    @Before
    public void logName()
    {
        log.info("Runtime id: {}", ManagementFactory.getRuntimeMXBean()
                .getName());
    }

    @After
    public void takeScreenShotSoICanTellWhatOnEarthIsGoingOn()
            throws Exception
    {
        driver.takeScreenshot(this.getClass().getSimpleName() + "-" + this.testName.getMethodName());
        Logs logs = driver.getWebDriver().manage().logs();
        log.info("Log types: {}", logs.getAvailableLogTypes());
        log.info("Driver log: {}", logs.get("driver").getAll());
        log.info("Browser log: {}", logs.get("browser").getAll());
        driver.close();
    }

    @Test
    public void shouldShowPickUserWhenNoUserSelected() {
        NavBar nav = driver.navigationBar();
        Map<String, String> labels = nav.itemLabels();
        assertThat(labels, hasEntry(equalTo(PICK_USER), not(containsString(A_USER))));

        for (String label: labels.keySet()) {
            log.info("Visiting {}", label);
            nav.visitPage(label);
            Map<String, String> currentPageLabels = nav.itemLabels();
            log.info("nav labels: {}", currentPageLabels);
            assertThat(currentPageLabels, hasEntry(equalTo(PICK_USER), not(containsString(A_USER))));
        }
    }

    @Test
    public void shouldShowTimeSheetsWhenSomeUserSelected() {
        driver.visitTimeSheet(A_USER, THE_DATE);

        NavBar nav = driver.navigationBar();
        Map<String, String> labels = nav.itemLabels();
        assertThat(labels, hasEntry(equalTo(TIME_SHEETS), containsString(A_USER)));

        for (String label: labels.keySet()) {
            log.info("Visiting {}", label);
            nav.visitPage(label);
            Map<String, String> currentPageLabels = nav.itemLabels();
            log.info("nav labels: {}", currentPageLabels);
            assertThat(currentPageLabels, hasEntry(equalTo(TIME_SHEETS), containsString(A_USER)));
        }
    }
}
