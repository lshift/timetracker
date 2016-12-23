DO
$users$
BEGIN
    IF NOT EXISTS (SELECT * FROM pg_catalog.pg_user WHERE usename = 'timetracker') THEN
        CREATE ROLE timetracker;
        ALTER ROLE timetracker WITH NOSUPERUSER INHERIT NOCREATEROLE NOCREATEDB LOGIN PASSWORD 'md5b7527c0bc09856308b5108ccf6889848';
        CREATE ROLE timezilla;
    END IF;
END
$users$;
