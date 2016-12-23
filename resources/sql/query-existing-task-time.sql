SELECT tt.*
FROM "task_time" tt
	INNER JOIN users u ON u.id = tt.user_id
WHERE start_time >= :start_time :: TIMESTAMP WITH TIME ZONE
	AND end_time <= :end_time :: TIMESTAMP WITH TIME ZONE
	AND u.name = :user_name
ORDER BY "start_time"
