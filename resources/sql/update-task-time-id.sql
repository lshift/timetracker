UPDATE "task_time"
SET "task_id" = :new_task_id
FROM users u
WHERE task_id = :old_task_id
        AND user_id = u.id
        AND end_time > :start_time :: TIMESTAMP WITH TIME ZONE
        AND start_time < :end_time :: TIMESTAMP WITH TIME ZONE
        AND u.name = :user_name
