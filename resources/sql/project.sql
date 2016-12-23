-- name: create<!
INSERT INTO "projects" ("name", "active") VALUES (:name, TRUE)

-- name: delete!
DELETE FROM "projects" WHERE id = :id

-- name: exists
SELECT * FROM "projects" WHERE (name = :name)

-- name: get-entity
SELECT * FROM "projects" WHERE (id = :id)

-- name: list-entities
SELECT * FROM "projects"

-- name: update!
UPDATE "projects" SET name = :name, active = :active WHERE id = :id
