package net.lshift.project.timetracker;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.base.MoreObjects;

import java.util.Objects;

public class Activity
{
    public String id;
    public String name;
    public boolean active;

    @JsonCreator
    public Activity()
    {
    }

    public Activity(String id, String name)
    {
        this.id = id;
        this.name = name;
    }

    public String toString()
    {
        return MoreObjects.toStringHelper(this).add("id", id).add("name", name)
                        .toString();
    }
    public int hashCode() {
        return Objects.hash(id, name);
    }
    public boolean equals(Object that) {
        if(that == null || !Objects.equals(getClass(), that.getClass())) {
            return false;
        };
        Activity other = (Activity) that;

        return Objects.equals(id, other.id) &&
                Objects.equals(name, other.name);
    }
}
