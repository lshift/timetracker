package net.lshift.project.timetracker;

import static net.lshift.project.timetracker.AppDriver.TIMEOUT;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class TimeSheetPage
{
    private WebDriver driver;
    private final Logger log = LoggerFactory.getLogger(TimeSheetPage.class);

    TimeSheetPage(WebDriver driver, String aUser, LocalDate theDate)
    {
        this.driver = driver;
    }

    public TimeSheetRow givenATaskLike(Task workItem) {
        return TimeSheetRow.untilTaskLike(driver, workItem);
    }

    public TimeSheetRow newTaskRow()
    {
        By sel = By.cssSelector(".new-time-sheet-tasks .editable-time-sheet-task");
        AppDriver.untilPresent(driver, TIMEOUT, sel);
        WebElement row = driver.findElement(sel);
        return new TimeSheetRow.NewTaskRow(row, driver);
    }

    public TimeSheetRow idleTimeBar()
    {
        By sel = By.cssSelector(".idle-time-sheet-task");
        AppDriver.untilPresent(driver, TIMEOUT, sel);
        WebElement row = driver.findElement(sel);
        return TimeSheetRow.of(driver, row);
    }

    public void whenIGoForwardADay()
    {
        dateControlClick(By.className("next-day"));
    }

    public void whenIGoBackwardADay()
    {
        dateControlClick(By.className("prev-day"));
    }

    private void dateControlClick(By sel)
    {
        LocalDate prevDate = shownDate();
        driver.findElement(sel).click();
        AppDriver.untilReady(TIMEOUT, () -> shownDate(),
            shownDate -> !Objects.equals(shownDate, prevDate));
    }

    private String getDateString()
    {
        String wholeString = driver.findElement(By.className("current-date"))
                        .getText();
        log.info("Date string: {}", wholeString);
        Matcher matches = Pattern.compile("[^ ]*").matcher(wholeString);
        if (!matches.find()) {
            throw new RuntimeException("No match for date string:"
                            + wholeString);
        }
        return matches.group();
    }

    public LocalDate shownDate()
    {
        return ISODateTimeFormat.date().parseLocalDate(getDateString());
    }

    class ThisWeekSummary {
        private final WebElement element;
        ThisWeekSummary(WebElement element) {
            this.element = element;
        }

        class ProjectBar {
            private final WebElement element;
            private String label;

            ProjectBar(WebElement element) {
                this.element = element;
            }

            String hoverLabel() {
                if (label == null) {
                    Actions actions = new Actions(driver);
                    actions.moveToElement(element).perform();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        log.error("{}", e);
                    }
                    label = element.findElement(By.tagName("text")).getText();
                }
                return label;
            }

            double widthPercentage() {
                String width = element.findElement(By.tagName("rect")).getAttribute("width");
                if (width == null || !width.endsWith("%")) {
                    throw new RuntimeException("ProjectBar width not found or not a percentage, width: " + width);
                }
                return Double.parseDouble(width.substring(0, width.length() - 1));
            }
        }

        class DayBar {
            private final WebElement element;

            DayBar(WebElement element) {
                this.element = element;
            }

            double heightPixels() {
                return Double.parseDouble(element.getAttribute("height"));
            }
        }

        List<ProjectBar> projectBars() {
            return element.findElements(By.className("project-bar"))
                .stream()
                .map(ProjectBar::new)
                .collect(Collectors.toList());
        }

        List<DayBar> dayBars() {
            return element.findElements(By.className("day-bar"))
                .stream()
                .map(DayBar::new)
                .collect(Collectors.toList());
        }
    }

    public ThisWeekSummary thisWeekSummary() {
        return new ThisWeekSummary(driver.findElement(By.className("this-week-summary")));
    }
}
