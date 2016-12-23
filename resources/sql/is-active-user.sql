SELECT EXISTS (SELECT * FROM users u WHERE (u.active IS TRUE) AND u.name = :user_name) as activep;
