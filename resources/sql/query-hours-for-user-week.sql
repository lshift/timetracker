SELECT p.id, p.name, coalesce(date_part('epoch', sum(end_time - start_time)) / 3600, 0.0) as hours
FROM task_time tt
  JOIN tasks t ON tt.task_id = t.id
  LEFT JOIN users u on tt.user_id = u.id
  LEFT JOIN projects p on t.project_id = p.id
WHERE CASE WHEN :userid :: integer IS NOT NULL THEN u.id = :userid :: integer ELSE TRUE END
  AND CASE WHEN :projectid :: integer IS NOT NULL THEN t.project_id = :projectid :: integer ELSE TRUE END
  AND start_time >= :week_start :: date
  AND start_time < :week_end :: date
GROUP BY p.id, p.name;
