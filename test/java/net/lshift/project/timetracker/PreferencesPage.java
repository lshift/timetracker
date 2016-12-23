package net.lshift.project.timetracker;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Created by ceri on 05/12/16.
 */
public class PreferencesPage {
    private final RemoteWebDriver webDriver;
    private final static By RESET_ON_NEW_TASK_BOX = By.id("reset-on-new-task");

    public PreferencesPage(RemoteWebDriver webDriver) {
        this.webDriver = webDriver;
    }

    public static PreferencesPage of(RemoteWebDriver webDriver) {
        return new PreferencesPage(webDriver);
    }

    public void resetNewTaskFields(boolean resetp) {
        WebElement checkbox = webDriver.findElement(RESET_ON_NEW_TASK_BOX);

        if(checkbox.isSelected() != resetp) {
            checkbox.click();
        };
        assertThat(checkbox.isSelected(), equalTo(resetp));
    }
}
