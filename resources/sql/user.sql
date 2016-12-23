-- name: create<!
INSERT INTO "users" ("name", "active") VALUES (:name, TRUE)

-- name: delete!
DELETE FROM "users" WHERE id = :id

-- name: exists
SELECT * FROM "users" WHERE (name = :name)

-- name: get-entity
SELECT * FROM "users" WHERE (id = :id)

-- name: list-entities
SELECT * FROM "users"

-- name: update!
UPDATE "users" SET name = :name, active = :active WHERE id = :id
