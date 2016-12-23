INSERT INTO "task_time" ("task_id", "user_id", "start_time", "end_time")
SELECT :task_id, id, :start_time :: TIMESTAMP WITH TIME ZONE, :end_time :: TIMESTAMP WITH TIME ZONE
FROM users AS u
WHERE u.name = :user_name
