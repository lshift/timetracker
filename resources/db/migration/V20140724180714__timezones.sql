begin;

CREATE TEMPORARY TABLE saved_ddl AS
	SELECT v.table_schema, v.table_name, v.view_definition
	FROM information_schema.views AS v
	WHERE v.table_schema IN ('public', 'reporting');

CREATE FUNCTION stash_views() RETURNS void AS $$
DECLARE r record;
BEGIN
	FOR r in SELECT table_schema, table_name FROM saved_ddl
	LOOP
		EXECUTE 'DROP VIEW ' || quote_ident(r.table_schema) || '.' || quote_ident(r.table_name);
	END LOOP;
END$$ LANGUAGE plpgsql;

CREATE FUNCTION unstash_views() RETURNS void AS $$
DECLARE r record;
BEGIN
	FOR r in SELECT table_schema, table_name, view_definition FROM saved_ddl
	LOOP
		EXECUTE 'CREATE VIEW ' || quote_ident(r.table_schema) || '.'    || quote_ident(r.table_name) || ' AS ' || r.view_definition;
		EXECUTE 'ALTER TABLE ' || quote_ident(r.table_schema) || '.'    || quote_ident(r.table_name) || ' OWNER TO timetracker';
		EXECUTE 'REVOKE ALL ON TABLE ' || quote_ident(r.table_schema) || '.'  || quote_ident(r.table_name) || ' FROM PUBLIC';
		EXECUTE 'REVOKE ALL ON TABLE ' || quote_ident(r.table_schema) || '.'  || quote_ident(r.table_name) || ' FROM timetracker';
		EXECUTE 'GRANT ALL ON TABLE '   || quote_ident(r.table_schema) || '.' || quote_ident(r.table_name) || ' TO timetracker';
		EXECUTE 'GRANT SELECT ON TABLE ' || quote_ident(r.table_schema) || '.' || quote_ident(r.table_name) || ' TO PUBLIC';
	END LOOP;
END$$ LANGUAGE plpgsql;

select stash_views();

ALTER TABLE task_time
	ALTER COLUMN start_time TYPE timestamp with time zone,
	ALTER COLUMN end_time TYPE timestamp with time zone;

select unstash_views();

DROP FUNCTION stash_views();
DROP FUNCTION unstash_views();

commit;
