package net.lshift.project.timetracker;

import com.google.common.base.MoreObjects;

import java.util.Objects;

public class Task
{
    public String bugId;
    Project project;
    Activity activity;
    String description;

    public Task(Project lshift, Activity activity, String bugId,
                String description)
    {
        this.project = lshift;
        this.activity = activity;
        this.bugId = bugId;
        this.description = description;
    }

    public String toString()
    {
        return MoreObjects.toStringHelper(this).add("projectId", project)
                        .add("activityId", activity).add("bugId", bugId)
                        .add("description", description).toString();
    }

    public int hashCode() {
        return Objects.hash(bugId, project, activity, description);
    }
    public boolean equals(Object that) {
        if(!Objects.equals(getClass(), that.getClass())) {
            return false;
        };
        Task other = (Task) that;

        return Objects.equals(project, other.project) &&
                Objects.equals(activity, other.activity) &&
                Objects.equals(bugId, other.bugId) &&
                Objects.equals(description, other.description);
    }
}
