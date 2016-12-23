package net.lshift.project.timetracker;

import static net.lshift.project.timetracker.Constants.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.Interval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

public class TimeSheetData
{
    public int id;
    public String project_id;
    public String activity_id;
    public String bug;
    public String description;
    public TimeSheetData.TSPeriod[] times;

    @JsonCreator
    TimeSheetData()
    {
    }

    public List<Interval> periods()
    {
        // TODO Auto-generated method stub
        return Arrays.asList(times).stream().map(p -> p.toInterval())
                        .collect(Collectors.toList());
    }

    public String toString()
    {
        return MoreObjects.toStringHelper(this).add("id", id)
                        .add("project_id", project_id)
                        .add("activity_id", activity_id).add("bug", bug)
                        .add("description", description)
                        .add("periods", Arrays.asList(times)).toString();
    }

    static class TSPeriod
    {
        private Interval value;

        @JsonCreator
        TSPeriod(@JsonProperty("start_time") String start_time,
                 @JsonProperty("end_time") String end_time)
        {
            DateTime start = Constants.TIME_FORMAT.parseDateTime(start_time);
            DateTime end = Constants.TIME_FORMAT.parseDateTime(end_time);
            this.value = new Interval(
                            start.withZone(Constants.EUROPE_LONDON),
                            end.withZone(Constants.EUROPE_LONDON));
        };

        public Interval toInterval()
        {
            return value;
        }

        public String toString()
        {
            return MoreObjects.toStringHelper(this).add("value", value)
                            .toString();
        }
    }

    public boolean matches(Task task1)
    {
        return Objects.equals(project_id, task1.project.id)
                        && Objects.equals(activity_id, task1.activity.id)
                        && Objects.equals(bug, task1.bugId)
                        && Objects.equals(description, description);
    }

}
