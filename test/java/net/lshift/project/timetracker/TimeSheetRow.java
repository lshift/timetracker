package net.lshift.project.timetracker;

import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.IsArrayWithSize.arrayWithSize;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.joda.time.Interval;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Action;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeSheetRow
{
    final static Logger log = LoggerFactory.getLogger(TimeSheetRow.class);

    private static final DateTimeFormatter BASIC_DT_FORMAT = ISODateTimeFormat
                    .basicDateTimeNoMillis();
    public static final String NEW_TASK = "new-task";
    public static final By PROJECT = By.name("project");
    public static final By ACTIVITY = By.name("activity");
    public static final By BUG = By.name("bug");
    public static final By DESCRIPTION = By.name("description");
    public static final By FOLLOWING_HIDDEN_INPUT_SIBLING = By.xpath("./following-sibling::input[@type='hidden']");
    private final String rowId;
    protected WebDriver webDriver;

    public TimeSheetRow(WebElement row, WebDriver driver)
    {
        this.rowId = row.getAttribute("id").trim();
        assertThat("rowid", rowId, not(isEmptyOrNullString()));
        this.webDriver = driver;
    }

    static TimeSheetRow untilTaskLike(WebDriver driver, Task workItem)
    {
        Optional<TimeSheetRow> matches = AppDriver.untilReady(
            () -> findTask(driver, workItem), o -> o.isPresent());

        return matches.orElseThrow(() -> new RuntimeException(
            "Could not find row like: " + workItem));
    }

    static Optional<TimeSheetRow> findTask(WebDriver driver, Task workItem)
    {
        return getRows(driver)
            .stream()
            .filter(row -> row.findElement(By.className("bug")).getText().contains(workItem.bugId))
            .limit(1)
            .map(row -> TimeSheetRow.of(driver, row))
            .findFirst();
    }

    static List<WebElement> getRows(WebDriver driver)
    {
        return AppDriver.untilPresent(driver, By.cssSelector(".time-sheet-task"));
    }

    public void blockOutTime(LocalTime start, LocalTime end)
    {
        WebElement startPos = timePosition(start);
        WebElement endPos = timePosition(end);
        Action selectTimePeriod = new Actions(webDriver).clickAndHold(startPos)
                        .moveToElement(endPos).release(endPos).build();
        selectTimePeriod.perform();
    }

    private Interval parseIntervalFromTitle(WebElement e)
    {
        String[] periods = e.getAttribute("title").split("-");
        assertThat(periods, arrayWithSize(2));
        TimeSheetScreenIT.log.info("Times: {}", Arrays.asList(periods));
        LocalDateTime startTime = BASIC_DT_FORMAT.parseLocalDateTime(periods[0]);
        LocalDateTime endTime = BASIC_DT_FORMAT.parseLocalDateTime(periods[1]);
        return new Interval(startTime.toDateTime(Constants.EUROPE_LONDON),
                        endTime.toDateTime(Constants.EUROPE_LONDON));
    }

    private WebElement timePosition(LocalTime time)
    {
        WebElement svg = row().findElement(By.tagName("svg"));
        String className = String.format("time-%d-%d", time.getHourOfDay(),
            time.getMinuteOfHour());
        WebElement tick = svg.findElement(By.className(className));
        return tick;
    }

    public static TimeSheetRow of(WebDriver webDriver, WebElement row)
    {
        return new TimeSheetRow(row, webDriver);
    }

    public Set<Interval> getPeriods()
    {
        Set<Interval> res = row().findElements(By.className("time-bar")).stream()
                        .map(e -> parseIntervalFromTitle(e))
                        .collect(Collectors.toSet());
        TimeSheetScreenIT.log.info("Fetched intervals from {}: {}",
            rowId, res);
        return res;
    }

    public TimeSheetRow editTaskDescription(Task target)
    {
        log.info("Looking at row id: {}",rowId);
        assertThat(rowId, notNullValue());
        new Actions(webDriver).doubleClick(row()).build().perform();
        Supplier<Optional<WebElement>> f = () -> findElts(webDriver,
            By.id(rowId)).flatMap(e -> findElts(e, By.tagName("input")))
                        .findFirst();

        Predicate<Optional<WebElement>> p = it -> it.isPresent();
        AppDriver.untilReady(f, p);

        AppDriver.repeatAction(5, () -> {
            fillInput(PROJECT, target.project.name + "\t");
            fillInput(ACTIVITY, target.activity.name + "\t");
            fillInput(BUG, target.bugId + "\t");
            fillInput(DESCRIPTION, target.description + "\t");

            if (Objects.equals(rowId, NEW_TASK)) {
                row().findElement(By.name("description")).sendKeys("\n");
            } else {
                row().findElement(By.className("commit")).click();
            };
            return null;
        });
        return this;
    }

    private WebElement row() {
        return webDriver.findElement(By.id(rowId));
    }

    private Stream<WebElement> findElts(SearchContext e, By query)
    {
        try {
            return e.findElements(query).stream();
        } catch (StaleElementReferenceException ex) {
            TimeSheetScreenIT.log.info("q: {} -> {}", query, ex);
            return Stream.empty();
        }
    }

    private void fillInput(By qualifier, String value)
    {
        WebElement input = row().findElement(qualifier);
        log.info("Change: {}, from:{}, to:{}", qualifier,
                valueOrNull(input), value);
        input.clear();
        input.sendKeys(value);
    }

    void blockOutTimeAndWait(LocalTime start, LocalTime end)
    {
        Set<Interval> currentPeriods = getPeriods();
        blockOutTime(start, end);
        AppDriver.untilReady(
            () -> getPeriods(),
            pds -> !Objects.equals(pds, currentPeriods));
    }

    public Task description() {
        return AppDriver.repeatAction(5, () -> {
            WebElement row = row();

            WebElement projectInput = row.findElement(PROJECT);
            WebElement projectId = projectInput.findElement(FOLLOWING_HIDDEN_INPUT_SIBLING);
            Project project = whenValuesNotNull(projectInput, projectId, Project::new);
            WebElement activityInput = row.findElement(ACTIVITY);
            WebElement activityId = activityInput.findElement(FOLLOWING_HIDDEN_INPUT_SIBLING);
            Activity activity = whenValuesNotNull(activityInput, activityId, Activity::new);

            String bug = valueOrNull(row.findElement(BUG));
            String descr = valueOrNull(row.findElement(DESCRIPTION));

            return new Task(
                    project, activity, bug, descr
            );
        });
    }

    private <T> T whenValuesNotNull(WebElement projectInput, WebElement projectId, BiFunction<String, String, T> f) {
        String a = valueOrNull(projectId);
        String b = valueOrNull(projectInput);
        if (a != null && b != null) {
            return f.apply(a, b);
        } else {
            return null;
        }
    }

    private String valueOrNull(WebElement input) {
        String value = input.getAttribute("value");
        if (value.isEmpty()) {
            return null;
        } else {
            return value;
        }
    }

    // The purpose of this sub-class is to implement unique logic for blockOutTimeAndWait
    // The current behaviour of waiting for the periods to change won't work for
    // the new task scenario as it will actually result in a new row being added.
    // So we wait on that instead.
    static class NewTaskRow extends TimeSheetRow {
        private Task task;

        public NewTaskRow(WebElement row, WebDriver driver) {
            super(row, driver);
        }

        @Override
        public TimeSheetRow editTaskDescription(Task task) {
            this.task = task;
            return super.editTaskDescription(task);
        }

        @Override
        void blockOutTimeAndWait(LocalTime start, LocalTime end) {
            blockOutTime(start, end);
            if (task == null) {
                throw new RuntimeException("NewTaskRow#blockOutTimeAndWait called, but no task has been given");
            }
            TimeSheetRow.untilTaskLike(webDriver, task);
        }
    }
}
