with x as (
  select u.id, u.name,
  date_part('epoch', sum(
       case when (end_time >= date 'today' - interval '1 month') then (tt.end_time - tt.start_time)
       else interval '0' /* mark people outside of date range as "no time" */
  end)) / 3600 :: int as worked_hours,
  max(tt.end_time) last_entry
  from users u left join task_time tt on tt.user_id = u.id
  where u.active
  group by u.id, u.name)
select name, last_entry,
  worked_hours::int / 8::int as days,
  worked_hours::int % 8::int as hours
from x
order by worked_hours desc, last_entry asc, name asc
