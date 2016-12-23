package net.lshift.project.timetracker;

import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class Constants
{
    // APP_URL: The URL the web browser will navigate to
    static final String APP_URL = System.getenv("TIMETRACKER_APP_URL") != null ? System.getenv("TIMETRACKER_APP_URL") : "http://localhost:18000/";
    // SERVER_URL: The URL the tests will call the API directly. N.B. this can be
    // different to the above, e.g. when the browser is running inside Docker,
    // but the tests are being run on a developer's machine
    static final String SERVER_URL = System.getenv("TIMETRACKER_SERVER_URL") != null ? System.getenv("TIMETRACKER_SERVER_URL") : "http://localhost:18000/";
    // SELENIUM_URL: The URL Selenium is running under
    static final String SELENIUM_URL = System.getenv("SELENIUM_URL") != null ? System.getenv("SELENIUM_URL") : "http://localhost:4444/wd/hub";

    static final String RECENT_HOURS = "168";
    static final String LSHIFT_PROJECT_ID = "38";
    static final String LSHIFT_RD_PROJECT_ID = "64";
    static final String DEV_ACTIVITY_ID = "7";
    static final String QA_ACTIVITY_ID = "12";
    static final String INVESTIGATION_ACTIVITY_ID = "25";

    static final Project LSHIFT = new Project(LSHIFT_PROJECT_ID, "LShift");
    static final Project LSHIFT_RD = new Project(LSHIFT_RD_PROJECT_ID,
                    "LShift R&D");
    static final Activity DEV_ACTIVITY = new Activity(DEV_ACTIVITY_ID,
                    "Development");
    static final Activity QA_ACTIVITY = new Activity(QA_ACTIVITY_ID, "QA");
    static final Activity INVESTIGATION_ACTIVITY = new Activity(INVESTIGATION_ACTIVITY_ID,
                    "Investigation");

    static final Task TASK1 = new Task(LSHIFT, DEV_ACTIVITY, "XY-23",
                    "Artichokes");
    static final Task TASK1_MODIFIED = new Task(LSHIFT_RD, QA_ACTIVITY,
                    "XY-24", "Spam");

    static final Task TASK2 = new Task(LSHIFT_RD, INVESTIGATION_ACTIVITY, "XY-42",
                    "Stuff");

    static final LocalDate THE_DATE = new LocalDate(2014, 1, 24);
    static final LocalDate THE_NEXT_DAY = THE_DATE.plus(Days.ONE);
    static final LocalDate SEED_DATE = THE_DATE.minusDays(2);

    static final DateTimeZone EUROPE_LONDON = DateTimeZone
                    .forID("Europe/London");
    static final String A_USER = "paulj";
    static final String ANOTHER_USER = "Michal";
    static final DateTimeFormatter TIME_FORMAT = ISODateTimeFormat.dateTime();

}
