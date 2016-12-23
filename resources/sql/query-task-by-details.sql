SELECT id FROM "tasks" WHERE (("project_id" = :project_id) AND ("activity_id" = :activity_id) AND ("bug" = :bug) AND ("description" = :description)) LIMIT 1
