-- name: create<!
INSERT INTO "activities" ("name", "active") VALUES (:name, TRUE)

-- name: delete!
DELETE FROM "activities" WHERE id = :id

-- name: exists
SELECT * FROM "activities" WHERE (name = :name)

-- name: get-entity
SELECT * FROM "activities" WHERE (id = :id)

-- name: list-entities
SELECT * FROM "activities"

-- name: update!
UPDATE "activities" SET name = :name, active = :active WHERE id = :id
