package net.lshift.project.timetracker;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.LocalDate;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static net.lshift.project.timetracker.AppDriver.untilPresent;
import static net.lshift.project.timetracker.AppDriver.untilReady;
import static net.lshift.project.timetracker.WebDriverMatchers.hasElement;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

public class TimezillaPage {
    private static final Logger log = LoggerFactory.getLogger(TimezillaPage.class);
    final RemoteWebDriver webDriver;

    public TimezillaPage(RemoteWebDriver webDriver) {
        this.webDriver = webDriver;
    }

    public static TimezillaPage of(RemoteWebDriver webDriver) {
        By q = By.className("timezilla-query-page");
        untilPresent(webDriver, q);
        assertThat(webDriver, hasElement(q, notNullValue(WebElement.class)));
        return new TimezillaPage(webDriver);
    }

    public Set<String> availableFilters() {
        return webDriver.findElements(By.tagName("h3"))
            .stream()
            .map(WebElement::getText)
            .collect(toSet());
    }

    /**
     * Waits for the latest report to load (if there is a request to the server
     * that hasn't been returned yet)
     */
    public void waitForReport() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            log.warn("" + e);
        }
        untilReady(
            () -> (Boolean) webDriver.executeScript("return ! time_tracker.timezilla_ui.waiting_on_requests_QMARK_()"),
            (b) -> b == null ? false : b);
    }

    public void forceDate(LocalDate date) {
        // This escape hatch is used for Timezilla UI to assert to the backend
        // what day it is. It's not exposed as functionality on the page
        // intentionally.
        webDriver.executeScript("time_tracker.timezilla_ui.set_today_BANG_(\"" + date + "\")");
        waitForReport();
    }

    private static String rowValFor(WebElement webElement, String desired) {
        try {
            return webElement.findElement(By.className("results-" + desired)).getText();
        }
        catch (NoSuchElementException ex) {
            return null;
        }
    }

    private static Set<String> commaSeparated(String s) {
        if (s == null)
            return null;
        return ImmutableSet.copyOf(s.split(", "));
    }

    public List<ReportRow> resultsRows() {
        return AppDriver.repeatAction(5, () -> webDriver
                .findElements(By.className("results-row"))
                .stream()
                .map(tr -> new ReportRow(
                        commaSeparated(rowValFor(tr, "user")),
                        commaSeparated(rowValFor(tr, "project")),
                        commaSeparated(rowValFor(tr, "activity")),
                        rowValFor(tr, "ticket"),
                        rowValFor(tr, "description"),
                        Double.parseDouble(rowValFor(tr, "days")),
                        Double.parseDouble(rowValFor(tr, "hours"))
                ))
                .collect(Collectors.toList()));
    }

    public enum ReportQueryBy { TIME, TASK, USER, PROJECT }
    public void setReportQuery(ReportQueryBy reportQueryBy) {
        webDriver.findElementById("BY_" + reportQueryBy.toString()).click();
        waitForReport();
    }

    private void select(String name, Collection<String> optionsToSelect) {
        Select select = new Select(webDriver.findElementByName(name));
        select.deselectAll();
        for (String option : optionsToSelect) {
            select.selectByVisibleText(option);
        }
        waitForReport();
    }

    public void selectProjects(Project... projects) {
        select("project", Arrays.stream(projects).map(p -> p.name).collect(Collectors.toList()));
    }

    public void selectUsers(String... users) {
        select("user", ImmutableList.copyOf(users));
    }

    public void selectActivities(Activity... activities) {
        select("activity", Arrays.stream(activities).map(a -> a.name).collect(Collectors.toList()));
    }

    public void setTicketIds(String... ticketIds) {
        WebElement element = webDriver.findElementByName("bug-numbers");
        element.clear();
        element.sendKeys(String.join(",", ticketIds));
        waitForReport();
    }

    public enum DateFrom { WEEK, LAST_WEEK, MONTH, LAST_MONTH, YEAR, ALL, USER_SPECIFIED }
    public enum DateTo { ALL, NOW, LAST_WEEK, LAST_MONTH, USER_SPECIFIED }

    public void selectDateRange(DateFrom from, DateTo to) {
        for (WebElement element : webDriver.findElementsByName("date-from")) {
            if (element.getAttribute("id").equals(from.name())) {
                element.click();
            }
        }
        for (WebElement element : webDriver.findElementsByName("date-to")) {
            if (element.getAttribute("id").equals(to.name())) {
                element.click();
            }
        }
        waitForReport();
    }

    public void selectDateRange(LocalDate from, LocalDate to) {
        WebElement dateFrom = webDriver.findElementByName("specific-date-from");
        WebElement dateTo = webDriver.findElementByName("specific-date-to");

        dateFrom.clear();
        dateFrom.sendKeys(from.toString());

        // This click works to defeat the fact that the clear is only working
        // intermittently without it
        dateTo.click();
        dateTo.clear();
        dateTo.sendKeys(to.toString());

        selectDateRange(DateFrom.USER_SPECIFIED, DateTo.USER_SPECIFIED);
    }

    double totalDays() {
        WebElement totalDaysElement = webDriver.findElement(By.id("total-days"));
        return Double.parseDouble(totalDaysElement.getText());
    }

    double totalHours() {
        WebElement totalHoursElement = webDriver.findElement(By.id("total-hours"));
        return Double.parseDouble(totalHoursElement.getText());
    }

    public static class ReportRow {
        public final Set<String> user;
        public final Set<String> project;
        public final Set<String> activity;
        public final String ticket;
        public final String taskDescription;
        public final double durationDays;
        public final double durationHours;

        public ReportRow(Set<String> user, Set<String> project, Set<String> activity, String ticket, String taskDescription, double durationDays, double durationHours) {
            this.user = user;
            this.project = project;
            this.activity = activity;
            this.ticket = ticket;
            this.taskDescription = taskDescription;
            this.durationDays = durationDays;
            this.durationHours = durationHours;
        }

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }
}
