SELECT p.id, p.name as name,
        SUM(date_part('epoch', tt.end_time - tt.start_time) :: float / 3600) AS hours
FROM task_time tt
        INNER JOIN users u ON u.id = tt.user_id
        INNER JOIN tasks t ON tt.task_id = t.id
        INNER JOIN projects p ON t.project_id = p.id
WHERE u.name = :user_name
        AND tt.start_time >= :start_date :: date
        AND tt.start_time < :end_date :: date
GROUP BY p.id, p.name;
