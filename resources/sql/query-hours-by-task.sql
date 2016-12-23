SELECT t.id, p.name as project, p.tracker_url, a.name as activity, t.bug, t.description,
  SUM(date_part('epoch', tt.end_time - tt.start_time) / 60 :: int) as minutes,
  STRING_AGG(DISTINCT u.name,', ') as user
FROM task_time tt
  JOIN tasks t on tt.task_id = t.id
  LEFT JOIN projects p on t.project_id = p.id
  LEFT JOIN activities a on t.activity_id = a.id
  LEFT JOIN users u on tt.user_id = u.id
WHERE (:has_userid is FALSE or u.id in (:userid))
  AND (:has_projectid is FALSE or t.project_id in (:projectid))
  AND (:has_activityid is FALSE or t.activity_id in (:activityid))
  AND (:has_bugid is FALSE or t.bug in (:bugid))
  AND start_time >= :week_start :: date
  AND start_time < :week_end :: date
  AND (:has_bugid is FALSE or t.bug in (:bugid))
GROUP BY t.id, p.name, a.name, t.bug, t.description, p.tracker_url
LIMIT :limit
