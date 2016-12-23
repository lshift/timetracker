package net.lshift.project.timetracker;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class WebDriverMatchers {
    public static Matcher<WebDriver> hasElement(
        final By selector,
        final Matcher<WebElement> next)
    {

        return new TypeSafeMatcher<WebDriver>() {
            @Override
            public void describeTo(Description description)
            {
                description.appendText("Matchers selector ").appendValue(
                    selector);
            }

            @Override
            protected boolean matchesSafely(WebDriver item)
            {
                WebElement e = item.findElement(selector);
                return next.matches(e);
            }

        };
    }

}
