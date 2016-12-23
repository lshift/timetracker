SELECT u.id, u.name as user,
  SUM(date_part('epoch', tt.end_time - tt.start_time) / 60 :: int) as minutes,
  STRING_AGG(DISTINCT p.name,', ') as project,
  STRING_AGG(DISTINCT a.name,', ') as activity
FROM task_time tt
  JOIN tasks t ON tt.task_id = t.id
  LEFT JOIN users u on tt.user_id = u.id
  LEFT JOIN projects p on t.project_id = p.id
  LEFT JOIN activities a on t.activity_id = a.id
WHERE (:has_userid is FALSE or u.id in (:userid))
  AND (:has_projectid is FALSE or t.project_id in (:projectid))
  AND (:has_activityid is FALSE or t.activity_id in (:activityid))
  AND (:has_bugid is FALSE or t.bug in (:bugid))
  AND start_time >= :week_start :: date
  AND start_time < :week_end :: date
GROUP BY u.id
LIMIT :limit
