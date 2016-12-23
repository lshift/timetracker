package net.lshift.project.timetracker;

import com.google.common.collect.ImmutableSet;
import net.lshift.project.timetracker.TimezillaPage.ReportRow;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.*;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static net.lshift.project.timetracker.Constants.*;
import static net.lshift.project.timetracker.TimezillaPage.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertFalse;

public class TimezillaIT {
    final static Logger log = LoggerFactory.getLogger(TimezillaIT.class);
    private final static LocalDate FIRST_SEPTEMBER = new LocalDate(2015, 9, 1); // Tuesday

    private static AppDriver driver;
    private static ServerApi server;
    private static TimezillaPage timezilla;

    // Report rows for all created user/tasks when filtering by time
    // The duration in days is always shown to 2 decimal places
    private static final ReportRow aUserTask1 = new ReportRow(set(A_USER), set(TASK1.project.name), set(TASK1.activity.name), TASK1.bugId, TASK1.description, 0.5, 3.5);
    private static final ReportRow aUserTask2 = new ReportRow(set(A_USER), set(TASK2.project.name), set(TASK2.activity.name), TASK2.bugId, TASK2.description, 0.43, 3);
    private static final ReportRow anotherUserTask1 = new ReportRow(set(ANOTHER_USER), set(TASK1.project.name), set(TASK1.activity.name), TASK1.bugId, TASK1.description, 0.57, 4);

    @Rule
    public TestName testName = new TestName();

    private static void clearMonthForUser(String user, LocalDate start) {
        server.setTimeIdle(user, start.toDateTimeAtStartOfDay().minusDays(1), start.toDateTimeAtStartOfDay().plusDays(31));
    }

    /**
     * Set up the expected scenario used by several of the tests below.
     * Two users do some work at the beginning of September, then we visit the
     * Timezilla page.
     */
    @BeforeClass
    public static void twoUsersWorkBeginningSeptember() throws Exception {
        driver = AppDriver.get();
        server = new ServerApi();

        clearMonthForUser(A_USER, FIRST_SEPTEMBER);
        clearMonthForUser(ANOTHER_USER, FIRST_SEPTEMBER);

        TimeSheetPage timeSheet = driver.visitTimeSheet(A_USER, FIRST_SEPTEMBER);
        timeSheet.newTaskRow()
            .editTaskDescription(TASK1)
            .blockOutTimeAndWait(new LocalTime(9, 0), new LocalTime(12, 30));
        timeSheet.newTaskRow()
            .editTaskDescription(TASK2)
            .blockOutTimeAndWait(new LocalTime(14, 0), new LocalTime(17, 0));

        timeSheet = driver.visitTimeSheet(ANOTHER_USER, FIRST_SEPTEMBER.plusDays(3));
        timeSheet.newTaskRow()
            .editTaskDescription(TASK1)
            .blockOutTimeAndWait(new LocalTime(12, 0), new LocalTime(16, 0));
    }

    @Before
    public void startWithCleanTimezillaPage() {
        timezilla = driver.visitTimezillaPage();
        timezilla.forceDate(FIRST_SEPTEMBER.plusDays(4));
        timezilla.selectDateRange(DateFrom.LAST_WEEK, DateTo.NOW);
    }

    @AfterClass
    public static void close() throws IOException {
        driver.close();
    }

    @After
    public void takeScreenshot() throws IOException {
        driver.takeScreenshot(this.getClass().getSimpleName() + "-" + this.testName.getMethodName());
    }

    @Test
    public void rendersInitialPage() throws InterruptedException {
        TimezillaPage timezilla = driver.visitTimezillaPage();
        Set<String> filters = timezilla.availableFilters();
        assertThat(filters, hasItems("Projects", "Activities", "Report Headings"));
    }

    @SafeVarargs
    private static <T> ImmutableSet<T> set(T... items) {
        return ImmutableSet.copyOf(items);
    }

    private void assertReportContains(ReportRow... expectedRows) {
        assertThat(timezilla.resultsRows(),
            containsInAnyOrder(expectedRows));
    }

    private void assertReportDoesNotContain(ReportRow... unexpectedRows) {
        List<ReportRow> actualRows = timezilla.resultsRows();
        for (ReportRow notExpected : unexpectedRows) {
            assertFalse(actualRows.contains(notExpected));
        }
    }

    @Test
    public void byTime() {
        timezilla.setReportQuery(ReportQueryBy.TIME);
        assertReportContains(aUserTask1, aUserTask2, anotherUserTask1);
    }

    @Test
    public void byTask() {
        timezilla.setReportQuery(ReportQueryBy.TASK);

        assertReportContains(
            new ReportRow(set(A_USER, ANOTHER_USER), set(TASK1.project.name), set(TASK1.activity.name), TASK1.bugId, TASK1.description, 1.07, 7.5),
            new ReportRow(set(A_USER), set(TASK2.project.name), set(TASK2.activity.name), TASK2.bugId, TASK2.description, 0.43, 3)
        );
    }

    @Test
    public void byUser() {
        timezilla.setReportQuery(ReportQueryBy.USER);

        assertReportContains(
            new ReportRow(set(A_USER), set(TASK1.project.name, TASK2.project.name), set(TASK1.activity.name, TASK2.activity.name), null, null, 0.93, 6.5),
            new ReportRow(set(ANOTHER_USER), set(TASK1.project.name), set(TASK1.activity.name), null, null, 0.57, 4)
        );
    }

    @Test
    public void byProject() {
        timezilla.setReportQuery(ReportQueryBy.PROJECT);

        assertReportContains(
            new ReportRow(null, set(TASK1.project.name), null, null, null, 1.07, 7.5),
            new ReportRow(null, set(TASK2.project.name), null, null, null, 0.43, 3)
        );
    }

    @Test
    public void projectFilter() {
        timezilla.selectProjects(TASK1.project, TASK2.project);
        assertReportContains(aUserTask1, aUserTask2, anotherUserTask1);

        timezilla.selectProjects(TASK1.project);
        assertReportContains(aUserTask1, anotherUserTask1);
        assertReportDoesNotContain(aUserTask2);
    }

    @Test
    public void userFilter() {
        timezilla.selectUsers(A_USER, ANOTHER_USER);
        assertReportContains(aUserTask1, aUserTask2, anotherUserTask1);

        timezilla.selectUsers(ANOTHER_USER);
        assertReportContains(anotherUserTask1);
        assertReportDoesNotContain(aUserTask1, aUserTask2);
    }

    @Test
    public void activityFilter() {
        timezilla.selectActivities(TASK1.activity, TASK2.activity);
        assertReportContains(aUserTask1, aUserTask2, anotherUserTask1);

        timezilla.selectActivities(TASK1.activity);
        assertReportContains(aUserTask1, anotherUserTask1);
        assertReportDoesNotContain(aUserTask2);
    }

    @Test
    public void ticketFilter() {
        timezilla.setTicketIds(TASK1.bugId, TASK2.bugId);
        assertReportContains(aUserTask1, aUserTask2, anotherUserTask1);

        timezilla.setTicketIds(TASK1.bugId);
        assertReportContains(aUserTask1, anotherUserTask1);
        assertReportDoesNotContain(aUserTask2);
    }

    // N.B. This test is not intended to be exhaustive
    @Test
    public void dateRanges() throws Exception {
        // August: Nothing there
        timezilla.selectDateRange(DateFrom.LAST_MONTH, DateTo.LAST_MONTH);
        assertReportDoesNotContain(aUserTask1, aUserTask2, anotherUserTask1);

        // We're now October, looking back at September
        timezilla.forceDate(FIRST_SEPTEMBER.plusMonths(1));
        assertReportContains(aUserTask1, aUserTask2, anotherUserTask1);

        // Checking last week
        timezilla.selectDateRange(DateFrom.LAST_WEEK, DateTo.LAST_WEEK);
        timezilla.forceDate(FIRST_SEPTEMBER.plusDays(4));
        assertReportDoesNotContain(aUserTask1, aUserTask2, anotherUserTask1);
        timezilla.forceDate(FIRST_SEPTEMBER.plusWeeks(1));
        assertReportContains(aUserTask1, aUserTask2, anotherUserTask1);

        // This month
        timezilla.selectDateRange(DateFrom.MONTH, DateTo.ALL);
        timezilla.forceDate(FIRST_SEPTEMBER.plusDays(15));
        assertReportContains(aUserTask1, aUserTask2, anotherUserTask1);
        timezilla.forceDate(FIRST_SEPTEMBER.plusMonths(1));
        assertReportDoesNotContain(aUserTask1, aUserTask2, anotherUserTask1);

        // Arbitrary dates
        timezilla.selectDateRange(new LocalDate(2015, 9, 1), new LocalDate(2015, 9, 5));
        assertReportContains(aUserTask1, aUserTask2, anotherUserTask1);
        timezilla.selectDateRange(new LocalDate(2015, 9, 3), new LocalDate(2015, 9, 5));
        assertReportContains(anotherUserTask1);
        assertReportDoesNotContain(aUserTask1, aUserTask2);
    }

    @Test
    public void mostFiltersWorkAcrossQueryTypes() {
        timezilla.selectProjects(TASK1.project);
        timezilla.selectUsers(A_USER);
        timezilla.selectActivities(TASK1.activity);
        timezilla.setTicketIds(TASK1.bugId);

        assertReportContains(aUserTask1);
        assertReportDoesNotContain(aUserTask2, anotherUserTask1);

        assertThat(timezilla.totalDays(), equalTo(0.5));
        assertThat(timezilla.totalHours(), equalTo(3.5));

        timezilla.setReportQuery(ReportQueryBy.TASK);

        assertThat(timezilla.totalDays(), equalTo(0.5));
        assertThat(timezilla.totalHours(), equalTo(3.5));

        timezilla.setReportQuery(ReportQueryBy.USER);

        assertThat(timezilla.totalDays(), equalTo(0.5));
        assertThat(timezilla.totalHours(), equalTo(3.5));
    }
}
