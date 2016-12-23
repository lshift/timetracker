package net.lshift.project.timetracker;

import com.google.common.io.Files;
import com.google.common.util.concurrent.Uninterruptibles;
import net.lshift.project.timetracker.griddle.Griddle;
import org.apache.commons.pool2.ObjectPool;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.ReadablePeriod;
import org.joda.time.Seconds;
import org.joda.time.format.ISODateTimeFormat;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static net.lshift.project.timetracker.Constants.*;

public class AppDriver
    implements Closeable
{
    final static Logger log = LoggerFactory.getLogger(AppDriver.class);
    public static final URI APP_URL_PARSED = URI.create(APP_URL);

    // Chrome requires the webdriver.chrome.driver system property to be set to
    // the location of the executable, e.g.
//     static { System.setProperty("webdriver.chrome.driver", "/path/to/chromedriver"); }
//     static ObjectPool<RemoteWebDriver> pool = Griddle.buildChromePool();

//    static ObjectPool<RemoteWebDriver> pool = Griddle.buildFirefoxPool();

    // Example for using Sauce Labs
    // You'll need your own username and API key - there's a 2 week free trial
    // N.B. you will need Sauce Connect running locally as a reverse proxy
//    static ObjectPool<RemoteWebDriver> pool;
//    static {
//        DesiredCapabilities capabilities = DesiredCapabilities.firefox();
//        capabilities.setCapability("platform", "Linux");
//        capabilities.setCapability("version", "45.0");
//        String driverUrl = "http://SAUCE_USERNAME:SAUCE_API_KEY@ondemand.saucelabs.com:80/wd/hub";
//        try {
//            pool = Griddle.buildRemoteWebDriver(new URL(driverUrl), capabilities);
//        } catch (MalformedURLException e) {
//            e.printStackTrace();
//        }
//    }

    static ObjectPool<RemoteWebDriver> pool;
    static {
//        DesiredCapabilities capabilities = DesiredCapabilities.chrome();
        DesiredCapabilities capabilities = DesiredCapabilities.firefox();

        try {
            pool = Griddle.buildRemoteWebDriver(new URL(SELENIUM_URL), capabilities);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(AppDriver::shutdown));
    }

    private static void shutdown()
    {
        log.info("Shutting down pool");
        pool.close();
    }

    public final static ReadablePeriod TIMEOUT = Seconds.seconds(10);

    private static final By PREFERENCES_NAV = By.id("preferences-link");
    private static final By TIMEZILLA_NAV = By.id("timezilla-link");
    private static final By TIME_SHEETS = By.linkText("Time sheets");
    private static final By PREFERENCES_PAGE = By.className("preferences-form");

    private RemoteWebDriver webDriver;

    public AppDriver(RemoteWebDriver webDriver)
    {
        this.webDriver = webDriver;
    }

    public static AppDriver get()
    {
        try {
            return new AppDriver(pool.borrowObject());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public TimeSheetPage visitTimeSheet(String aUser, LocalDate theDate)
    {
        verify();
        webDriver.get(APP_URL);
        String url = String.format("%s#/users/%s/%s", APP_URL, aUser,
            ISODateTimeFormat.basicDate().print(theDate));
        webDriver.get(url);

        /* Wait for Reagent to finish updating before returning */
        untilPresent(webDriver, TIMEOUT, TIME_SHEETS);

        return new TimeSheetPage(webDriver, aUser, theDate);
    }

    public void forceReload()
    {
        verify();
        String url = APP_URL + "/404?" + System.nanoTime();
        webDriver.get(url);
    }

    public TimezillaPage visitTimezillaPage()
    {
        verify();
        webDriver.get(APP_URL);
        webDriver.findElement(TIMEZILLA_NAV).click();
        return TimezillaPage.of(webDriver);
    }

    void takeScreenshot(String screenshotName)
        throws IOException
    {
        verify();

        String url = webDriver.getCurrentUrl();
        log.info("Preparing screenshot of: {}", url);

        byte[] screenshotBytes = webDriver.getScreenshotAs(OutputType.BYTES);

        new File("target/screenshots/").mkdirs();
        File to = new File(MessageFormat.format("target/screenshots/{0}-{1}.png",
            screenshotName, TIME_FORMAT.print(DateTime.now())));
        Files.write(screenshotBytes, to);

        log.info("Saved screenshot of {} to: {}", url, to);
    }

    void logPageSource()
    {
        verify();
        log.info("Page source of {} is", webDriver.getCurrentUrl());
        log.info(webDriver.getPageSource());
    }

    @Override
    public void close()
        throws IOException
    {
        try {
            pool.returnObject(webDriver);
            webDriver = null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public RemoteWebDriver getWebDriver()
    {
        return webDriver;
    }

    private void verify()
    {
        if (webDriver == null) {
            throw new IllegalStateException(
                            "Attempting to use closed appdriver");
        }
    }

    static <T> T untilReady(
        ReadablePeriod timeoutAfter,
        Supplier<T> fetcher,
        Predicate<T> isReady)
    {
        T value;
        DateTime timeout = DateTime.now().plus(timeoutAfter);
        int sleepTime = 100;
        for (value = fetcher.get(); (!isReady.test(value))
                && timeout.isAfterNow(); value = fetcher.get()) {
            Uninterruptibles.sleepUninterruptibly(sleepTime, TimeUnit.MILLISECONDS);
            sleepTime = (int) (sleepTime * 1.5);
        }
        if (!timeout.isAfterNow()) {
            log.info("Timed out with value {} after {}", value, timeoutAfter);
        } else {
            log.debug("Finished with value {}", value);
        }
        return value;
    }

    static <T> T untilReady(Supplier<T> fetcher, Predicate<T> isReady) {
        return untilReady(TIMEOUT, fetcher, isReady);
    }

    static List<WebElement> untilPresent(
        SearchContext elt,
        ReadablePeriod timeoutAfter,
        By query)
    {
        return AppDriver.untilReady(timeoutAfter,
            () -> elt.findElements(query),
            rs -> !rs.isEmpty());
    }

    static List<WebElement> untilPresent(SearchContext elt, By query) {
        return untilPresent(elt, TIMEOUT, query);
    }

    public NavBar navigationBar() {
        verify();
        ensureOnApplicationPage();
        return new NavBar(webDriver);
    }

    static <T> T repeatAction(int maxCount, Supplier<T> action) {

        long sleepTime = 5;
        for (int i=0; i<maxCount; i++) {
            try {
                return action.get();
            }
            catch (Exception e) {
                if (i == maxCount-1)
                    throw e;
                log.debug("Error on count {}: {}", i, e);
                Uninterruptibles.sleepUninterruptibly(sleepTime * 1<<i, TimeUnit.MILLISECONDS);
            }
        }
        throw new RuntimeException("Failed to either throw or get a result");
    }

    private void ensureOnApplicationPage() {
        URI curr = URI.create(webDriver.getCurrentUrl());
        if (!Objects.equals(curr.getPath(), APP_URL_PARSED.getPath())) {
            webDriver.get(APP_URL);
        }
    }

    public PreferencesPage preferences() {
        verify();
        webDriver.get(APP_URL);
        webDriver.findElement(PREFERENCES_NAV).click();
        untilPresent(webDriver, TIMEOUT, PREFERENCES_PAGE);
        return PreferencesPage.of(webDriver);

    }
}
