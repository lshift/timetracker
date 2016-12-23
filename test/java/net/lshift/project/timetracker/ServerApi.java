package net.lshift.project.timetracker;

import static org.hamcrest.collection.IsArrayWithSize.arrayWithSize;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static net.lshift.project.timetracker.Constants.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

class ServerApi
{
    static final String TIME_URL = SERVER_URL + "time";
    public static final String PROJECTS_URL = SERVER_URL + "projects";
    public static final String ACTIVITIES_URL = SERVER_URL + "activities";

    public TimeSheet fetchSheetFor(String aUser, LocalDate theDate)
    {
        DateTime startAt = theDate.toDateTimeAtStartOfDay(EUROPE_LONDON);

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

            String startDate = TIME_FORMAT.print(startAt);
            String endDate = TIME_FORMAT.print(startAt.plus(Days.ONE));
            TimeSheetScreenIT.log.info("Start:{}; end: {}", startDate, endDate);
            HttpUriRequest request = RequestBuilder.get(TIME_URL)
                            .addParameter("user", aUser)
                            .addParameter("start", startDate)
                            .addParameter("recent_hours", RECENT_HOURS)
                            .addParameter("end", endDate).build();

            CloseableHttpResponse response = httpclient.execute(request);
            TimeSheetScreenIT.log.info("Req: {}, Response: {}", request,
                response.getStatusLine());

            assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
            InputStream data = response.getEntity().getContent();

            ObjectMapper mapper = new ObjectMapper();
            CollectionType typeFactory = mapper.getTypeFactory()
                            .constructCollectionType(List.class,
                                TimeSheetData.class);
            ArrayList<TimeSheetData> readValue = mapper.readValue(data,
                typeFactory);
            TimeSheet timeSheet = new TimeSheet(readValue);
            TimeSheetScreenIT.log.info("Read timesheet: {}", timeSheet);
            return timeSheet;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void recordTime(
        String userName,
        String task_id,
        DateTime start,
        DateTime end)
    {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

            HttpUriRequest request = RequestBuilder
                            .post(TIME_URL + "/" + task_id)
                            .addParameter("user", userName)
                            .addParameter("start_time",
                                TIME_FORMAT.print(start))
                            .addParameter("end_time", TIME_FORMAT.print(end))
                            .build();

            CloseableHttpResponse response = httpclient.execute(request);
            TimeSheetScreenIT.log.info("Req: {}", request);
            TimeSheetScreenIT.log
                            .info("Response: {}", response.getStatusLine());

            assertThat(response.getStatusLine().getStatusCode(), equalTo(201));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String createTask(Task task)
    {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

            HttpUriRequest request = RequestBuilder.post(TIME_URL)
                            .addParameter("project_id", task.project.id)
                            .addParameter("activity_id", task.activity.id)
                            .addParameter("bug", task.bugId)
                            .addParameter("description", task.description)
                            .build();

            CloseableHttpResponse response = httpclient.execute(request);
            TimeSheetScreenIT.log.info("Req: {}", request);
            TimeSheetScreenIT.log
                            .info("Response: {}", response.getStatusLine());

            assertThat(response.getStatusLine().getStatusCode(), equalTo(201));

            assertThat(response.getHeaders("Location"), arrayWithSize(1));
            String location = response.getHeaders("Location")[0].getValue();
            TimeSheetScreenIT.log.info("Location: {}", location);

            Matcher p = Pattern.compile("^.*/(\\d+)$").matcher(location);
            assertThat(p.matches(), is(true));
            return p.group(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setTimeIdle(String aUser, DateTime from, DateTime to)
    {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

            HttpUriRequest request = RequestBuilder
                            .delete(TIME_URL)
                            .addParameter("user", aUser)
                            .addParameter("start_time", TIME_FORMAT.print(from))
                            .addParameter("end_time", TIME_FORMAT.print(to))
                            .build();

            CloseableHttpResponse response = httpclient.execute(request);
            TimeSheetScreenIT.log.info("Req: {}", request);
            TimeSheetScreenIT.log
                            .info("Response: {}", response.getStatusLine());

            assertThat(response.getStatusLine().getStatusCode(), equalTo(204));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Collection<Project> getProjects()
    {
        return getRefCollection(PROJECTS_URL, Project.class);

    }

    public Collection<Project> getActivities()
    {
        return getRefCollection(ACTIVITIES_URL, Activity.class);

    }

    private Collection<Project> getRefCollection(String url, Class<?> klass)
    {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

            HttpUriRequest request = RequestBuilder.get(url).build();

            CloseableHttpResponse response = httpclient.execute(request);
            TimeSheetScreenIT.log.info("Req: {}", request);
            TimeSheetScreenIT.log
                            .info("Response: {}", response.getStatusLine());

            assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

            ObjectMapper mapper = new ObjectMapper();
            CollectionType typeFactory = mapper.getTypeFactory()
                            .constructCollectionType(List.class, klass);
            return mapper.readValue(response.getEntity().getContent(),
                typeFactory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
