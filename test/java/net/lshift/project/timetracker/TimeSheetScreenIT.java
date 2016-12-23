package net.lshift.project.timetracker;

import net.lshift.project.timetracker.TimeSheetPage.ThisWeekSummary;
import org.hamcrest.Matcher;
import org.joda.time.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.logging.Logs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static net.lshift.project.timetracker.Constants.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;

public class TimeSheetScreenIT
{
    final static Logger log = LoggerFactory.getLogger(TimeSheetScreenIT.class);

    @Rule
    public TestName testName = new TestName();

    private static final int RETRIES = 5;

    private final ServerApi server = new ServerApi();

    private AppDriver driver;

    @Before
    public void setupSeedData() {
        // TimeSheetData sheet = server.fetchSheetFor(aUser, theDate);
        DateTime from = THE_DATE.toDateTimeAtStartOfDay(EUROPE_LONDON);
        DateTime to = from.plus(Days.TWO);
        server.setTimeIdle(A_USER, from, to);

        String task1_id = server.createTask(TASK1);
        String task2_id = server.createTask(TASK2);
        server.recordTime(A_USER, task1_id, timeOn(SEED_DATE, 15, 0),
            timeOn(SEED_DATE, 17, 0));
        server.recordTime(A_USER, task2_id, timeOn(SEED_DATE, 14, 0),
            timeOn(SEED_DATE, 15, 0));
    }

    public void clearDateForUser(String user, LocalDate date) {
        server.setTimeIdle(user, date.toDateTimeAtStartOfDay(), date.plusDays(1).toDateTimeAtStartOfDay());
    }

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

    private DateTime timeOn(LocalDate prevDate, int hourOfDay, int minutes)
    {
        return prevDate.toDateTime(new LocalTime(hourOfDay, minutes),
            EUROPE_LONDON);
    }

    @Test
    public void usersPageIsReallyAThing()
        throws Exception
    {
        driver.getWebDriver().get(APP_URL);
        WebElement elt = driver.getWebDriver().findElement(
            By.className("users-page"));
        assertThat(elt, notNullValue());
    }

    @Test
    public void canAddTimeToExistingTask()
        throws Exception
    {
        LocalTime start = new LocalTime(11, 0);
        LocalTime end = new LocalTime(13, 0);
        Interval expectedPeriod = new Interval(THE_DATE.toDateTime(start,
            EUROPE_LONDON), THE_DATE.toDateTime(end, EUROPE_LONDON));

        TimeSheetPage page = driver.visitTimeSheet(A_USER, THE_DATE);

        log.info("Task: {}", TASK1);
        TimeSheetRow taskRow = page.givenATaskLike(TASK1);

        TimeSheetRow taskRow1 = page.givenATaskLike(TASK1);

        taskRow1.blockOutTimeAndWait(start, end);

        assertThat(taskRow.getPeriods(), contains(expectedPeriod));

        TimeSheet data = server.fetchSheetFor(A_USER, THE_DATE);
        log.info("New sheet: {}", data);

        Collection<Interval> periods = data.periodsFor(TASK1);
        assertThat(periods, contains(expectedPeriod));

    }

    @Test
    public void canHandleOverlappingWorkItems()
        throws Exception
    {
        LocalTime start1 = new LocalTime(14, 0);
        LocalTime start2 = new LocalTime(15, 0);
        LocalTime end1 = new LocalTime(16, 0);
        LocalTime end2 = new LocalTime(17, 0);

        Interval expectedPeriod1 = new Interval(THE_DATE.toDateTime(start1,
            EUROPE_LONDON), THE_DATE.toDateTime(start2, EUROPE_LONDON));

        Interval expectedPeriod2 = new Interval(THE_DATE.toDateTime(start2,
            EUROPE_LONDON), THE_DATE.toDateTime(end2, EUROPE_LONDON));

        TimeSheetPage page = driver.visitTimeSheet(A_USER, THE_DATE);

        TimeSheetRow taskRow = page.givenATaskLike(TASK1);

        taskRow.blockOutTimeAndWait(start1, end1);
        TimeSheetRow taskRow2 = page.givenATaskLike(TASK2);

        taskRow2.blockOutTimeAndWait(start2, end2);

        TimeSheet data = server.fetchSheetFor(A_USER, THE_DATE);

        assertThat(page.givenATaskLike(TASK1).getPeriods(),
            contains(expectedPeriod1));
        assertThat(page.givenATaskLike(TASK2).getPeriods(),
            contains(expectedPeriod2));

        assertThat(data.periodsFor(TASK1), contains(expectedPeriod1));
        assertThat(data.periodsFor(TASK2), contains(expectedPeriod2));
    }

    @Test
    public void canAddNewTask()
        throws Exception
    {
        Task task = new Task(LSHIFT, QA_ACTIVITY, "ABC", "Something terrible");
        LocalTime start = new LocalTime(11, 0);
        LocalTime end = new LocalTime(13, 0);
        Interval expectedPeriod = new Interval(THE_DATE.toDateTime(start,
            EUROPE_LONDON), THE_DATE.toDateTime(end, EUROPE_LONDON));

        TimeSheetPage page = driver.visitTimeSheet(A_USER, THE_DATE);

        TimeSheetRow row = page.newTaskRow();
        row.editTaskDescription(task);
        row.blockOutTime(start, end);

        assertThat(page.givenATaskLike(task).getPeriods(),
            contains(expectedPeriod));
        assertThat(server.fetchSheetFor(A_USER, THE_DATE).periodsFor(task),
            contains(expectedPeriod));
    }

    @Test
    public void canEditTaskMetadata()
        throws Exception
    {
        LocalTime start = new LocalTime(11, 0);
        LocalTime end = new LocalTime(13, 0);
        Interval expectedPeriod = new Interval(THE_DATE.toDateTime(start,
            EUROPE_LONDON), THE_DATE.toDateTime(end, EUROPE_LONDON));

        TimeSheetPage page = driver.visitTimeSheet(A_USER, THE_DATE);

        TimeSheetRow row = page.givenATaskLike(TASK1);
        row.blockOutTime(start, end);
        row.editTaskDescription(TASK1_MODIFIED);

        Matcher<Iterable<? super Interval>> predicate = hasItem(expectedPeriod);

        AppDriver.untilReady(
                () -> page.givenATaskLike(TASK1_MODIFIED),
                (t) -> predicate.matches(t.getPeriods()));
        AppDriver.repeatAction(5, () -> {
            assertThat(page.givenATaskLike(TASK1_MODIFIED).getPeriods(),
                    predicate);
            return null;
        });

        assertThat(
                server.fetchSheetFor(A_USER, THE_DATE).periodsFor(
                        TASK1_MODIFIED), hasItem(expectedPeriod));
    }

    @Test
    public void canMarkTimeAsIdle()
        throws Exception
    {
        LocalTime projectStart = new LocalTime(10, 0);
        LocalTime projectEnd = new LocalTime(15, 0);
        LocalTime lunchStart = new LocalTime(12, 0);
        LocalTime lunchEnd = new LocalTime(13, 0);

        Interval beforeLunch = new Interval(THE_DATE.toDateTime(projectStart,
            EUROPE_LONDON), THE_DATE.toDateTime(lunchStart, EUROPE_LONDON));

        Interval afterLunch = new Interval(THE_DATE.toDateTime(lunchEnd,
            EUROPE_LONDON), THE_DATE.toDateTime(projectEnd, EUROPE_LONDON));

        TimeSheetPage page = driver.visitTimeSheet(A_USER, THE_DATE);

        page.givenATaskLike(TASK1)
                        .blockOutTimeAndWait(projectStart, projectEnd);
        page.idleTimeBar().blockOutTimeAndWait(lunchStart, lunchEnd);

        Matcher<Iterable<? extends Interval>> containsOnlyWorkedTime = containsInAnyOrder(
            beforeLunch, afterLunch);

        TimeSheet data = server.fetchSheetFor(A_USER, THE_DATE);
        assertThat(data.periodsFor(TASK1), containsOnlyWorkedTime);
        assertThat(page.givenATaskLike(TASK1).getPeriods(),
            containsOnlyWorkedTime);
    }

    @Test
    public void canEnterTimeOnDifferentDays()
    {
        LocalTime start1 = new LocalTime(14, 0);
        LocalTime end1 = new LocalTime(16, 0);
        LocalTime start2 = new LocalTime(15, 0);
        LocalTime end2 = new LocalTime(17, 0);

        Interval expectedPeriod1 = new Interval(THE_DATE.toDateTime(start1,
            EUROPE_LONDON), THE_DATE.toDateTime(end1, EUROPE_LONDON));

        Interval expectedPeriod2 = new Interval(THE_NEXT_DAY.toDateTime(start2,
            EUROPE_LONDON), THE_NEXT_DAY.toDateTime(end2, EUROPE_LONDON));

        TimeSheetPage page = driver.visitTimeSheet(A_USER, THE_DATE);
        page.givenATaskLike(TASK1).blockOutTimeAndWait(start1, end1);
        page.whenIGoForwardADay();
        assertThat(page.shownDate(), is(THE_NEXT_DAY));

        page.givenATaskLike(TASK1).blockOutTimeAndWait(start2, end2);
        assertThat(
            server.fetchSheetFor(A_USER, THE_NEXT_DAY).periodsFor(TASK1),
            contains(expectedPeriod2));

        page.whenIGoBackwardADay();
        assertThat(page.shownDate(), is(THE_DATE));

        assertThat(server.fetchSheetFor(A_USER, THE_DATE).periodsFor(TASK1),
            contains(expectedPeriod1));
        assertThat(page.givenATaskLike(TASK1).getPeriods(),
            contains(expectedPeriod1));

    }

    @Test
    public void summaryBarsShown() {
        TimeSheetPage page = driver.visitTimeSheet(A_USER, THE_DATE);
        page.givenATaskLike(TASK1).blockOutTimeAndWait(new LocalTime(10, 0), new LocalTime(12, 0));

        page.whenIGoForwardADay();
        page.givenATaskLike(TASK2).blockOutTimeAndWait(new LocalTime(10, 0), new LocalTime(14, 0));

        // Don't want the seeded time tracked - just the time we filled in above
        clearDateForUser(A_USER, SEED_DATE);

        page.whenIGoForwardADay();

        ThisWeekSummary weekSummary = page.thisWeekSummary();

        List<ThisWeekSummary.ProjectBar> projectBars = weekSummary.projectBars();
        // Labels are right
        assertThat(
            projectBars.stream().map(pb -> pb.hoverLabel()).collect(Collectors.toList()),
            containsInAnyOrder(TASK1.project.name, TASK2.project.name));

        // Proportions are right. 2 hours + 4 hours = 6 and the bar will show 8 hours (increments of 4)
        // Therefore 4/8 == 50% and 2/8 == 25%
        assertThat(
            projectBars.stream().map(pb -> pb.widthPercentage()).collect(Collectors.toList()),
            containsInAnyOrder(25.0, 50.0));

        List<ThisWeekSummary.DayBar> dayBars = weekSummary.dayBars();
        // Last two blocks are the ones we're interested in, and the last should be double the height
        // of the penultimate
        assertThat(
            dayBars.get(dayBars.size() - 1).heightPixels() / dayBars.get(dayBars.size() - 2).heightPixels(),
            equalTo(2.0));
    }


    @Test
    public void shouldPreserveNewTaskFieldsByDefault()
            throws Exception
    {
        Task task = new Task(LSHIFT, QA_ACTIVITY, "ABC", "Something terrible");
        LocalTime start = new LocalTime(11, 0);
        LocalTime end = new LocalTime(13, 0);
        driver.preferences().resetNewTaskFields(false);

        TimeSheetPage page = driver.visitTimeSheet(A_USER, THE_DATE);

        TimeSheetRow row = page.newTaskRow();
        row.editTaskDescription(task);
        row.blockOutTime(start, end);

        Task displayed = row.description();
        assertThat(displayed, equalTo(task));
    }

    @Test
    public void shouldResetNewTaskFieldOptionally()
            throws Exception
    {
        Task task = new Task(LSHIFT, QA_ACTIVITY, "ABC", "Something terrible");
        LocalTime start = new LocalTime(11, 0);
        LocalTime end = new LocalTime(13, 0);
        driver.preferences().resetNewTaskFields(true);

        TimeSheetPage page = driver.visitTimeSheet(A_USER, THE_DATE);

        TimeSheetRow row = page.newTaskRow();
        row.editTaskDescription(task);
        row.blockOutTime(start, end);

        Task blankTask = new Task(null, null, null, null);
        Task displayed = row.description();
        assertThat(displayed, equalTo(blankTask));
    }
}
