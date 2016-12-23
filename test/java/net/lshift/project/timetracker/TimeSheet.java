package net.lshift.project.timetracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.joda.time.Interval;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;

public class TimeSheet
{
    private ArrayList<TimeSheetData> tasks;

    public TimeSheet(ArrayList<TimeSheetData> readValue)
    {
        tasks = readValue;
    }

    public Optional<TimeSheetData> forTask(Task task1)
    {
        return tasks.stream().filter(it -> it.matches(task1)).findFirst();
    }

    public String toString()
    {
        return MoreObjects.toStringHelper(this)
                        .add("tasks", Arrays.asList(tasks)).toString();
    }

    Collection<Interval> periodsFor(Task task)
    {
        Optional<Collection<Interval>> map = forTask(task)
                        .map(d -> d.periods());
        TimeSheetScreenIT.log.info("{}", map);
        return map.orElse(Lists.newArrayList());
    }
}
