SELECT p.id, SUM(date_part('epoch', tt.end_time - tt.start_time) / 60 :: int) as minutes,
  p.name as project
FROM task_time tt
  JOIN tasks t ON tt.task_id = t.id
  LEFT JOIN projects p on t.project_id = p.id
WHERE (:has_userid is FALSE or tt.user_id in (:userid))
  AND (:has_projectid is FALSE or t.project_id in (:projectid))
  AND (:has_activityid is FALSE or t.activity_id in (:activityid))
  AND (:has_bugid is FALSE or t.bug in (:bugid))
  AND start_time >= :week_start :: date
  AND start_time < :week_end :: date
GROUP BY p.id
LIMIT :limit
