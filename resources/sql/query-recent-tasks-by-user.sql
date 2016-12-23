SELECT DISTINCT t.id, t.project_id, t.activity_id, t.bug, t.description
FROM "task_time" AS tt
	INNER JOIN "tasks" AS t ON t.id = tt.task_id
	INNER JOIN "users" AS u ON u.id = tt.user_id
WHERE (u.name = :user_name
	AND start_time >= :start_time :: TIMESTAMP WITH TIME ZONE
	AND end_time <= :end_time :: TIMESTAMP WITH TIME ZONE)
