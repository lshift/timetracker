--
-- PostgreSQL database dump
--

SET client_encoding = 'UTF8';
SET standard_conforming_strings = off;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET escape_string_warning = off;

SET search_path = public, pg_catalog;

--
-- Name: interval_round_up(interval, interval); Type: FUNCTION; Schema: public; Owner: timetracker
--

CREATE FUNCTION interval_round_up(interval, interval) RETURNS interval
    AS $_$
    SELECT ((CEIL(EXTRACT(EPOCH FROM $1) / EXTRACT(EPOCH FROM $2)) * EXTRACT(EPOCH FROM $2)) || 'second')::interval;
$_$
    LANGUAGE sql;


ALTER FUNCTION public.interval_round_up(interval, interval) OWNER TO timetracker;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: activities; Type: TABLE; Schema: public; Owner: timetracker; Tablespace: 
--

CREATE TABLE activities (
    id integer NOT NULL,
    name text NOT NULL,
    active boolean DEFAULT true
);


ALTER TABLE public.activities OWNER TO timetracker;

--
-- Name: activities_id_seq; Type: SEQUENCE; Schema: public; Owner: timetracker
--

CREATE SEQUENCE activities_id_seq
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;


ALTER TABLE public.activities_id_seq OWNER TO timetracker;

--
-- Name: activities_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: timetracker
--

ALTER SEQUENCE activities_id_seq OWNED BY activities.id;


--
-- Name: projects; Type: TABLE; Schema: public; Owner: timetracker; Tablespace: 
--

CREATE TABLE projects (
    id integer NOT NULL,
    name text NOT NULL,
    active boolean DEFAULT true
);


ALTER TABLE public.projects OWNER TO timetracker;

--
-- Name: task_time; Type: TABLE; Schema: public; Owner: timetracker; Tablespace: 
--

CREATE TABLE task_time (
    id integer NOT NULL,
    user_id integer NOT NULL,
    task_id integer NOT NULL,
    start_time timestamp without time zone NOT NULL,
    end_time timestamp without time zone NOT NULL
);


ALTER TABLE public.task_time OWNER TO timetracker;

--
-- Name: tasks; Type: TABLE; Schema: public; Owner: timetracker; Tablespace: 
--

CREATE TABLE tasks (
    id integer NOT NULL,
    project_id integer NOT NULL,
    activity_id integer NOT NULL,
    bug text,
    description text DEFAULT ''::text
);


ALTER TABLE public.tasks OWNER TO timetracker;

--
-- Name: users; Type: TABLE; Schema: public; Owner: timetracker; Tablespace: 
--

CREATE TABLE users (
    id integer NOT NULL,
    name text NOT NULL,
    active boolean DEFAULT true
);


ALTER TABLE public.users OWNER TO timetracker;

--
-- Name: excel_report_1; Type: VIEW; Schema: public; Owner: timetracker
--

CREATE VIEW excel_report_1 AS
    SELECT date_part('year'::text, tt.start_time) AS year, date_part('week'::text, tt.start_time) AS week, p.name AS project, sum((date_part('epoch'::text, interval_round_up((tt.end_time - tt.start_time), '00:15:00'::interval)) / (3600)::double precision)) AS duration, u.name FROM projects p, tasks t, task_time tt, users u WHERE ((((p.id = t.project_id) AND (t.id = tt.task_id)) AND (tt.user_id = u.id)) AND (tt.start_time > '2006-07-01 00:00:00'::timestamp without time zone)) GROUP BY date_part('year'::text, tt.start_time), date_part('week'::text, tt.start_time), p.name, u.name ORDER BY date_part('year'::text, tt.start_time) DESC, date_part('week'::text, tt.start_time) DESC, p.name, u.name;


ALTER TABLE public.excel_report_1 OWNER TO timetracker;

--
-- Name: excel_report_2_lshifttime; Type: VIEW; Schema: public; Owner: timetracker
--

CREATE VIEW excel_report_2_lshifttime AS
    SELECT date_part('year'::text, tt.start_time) AS year, date_part('week'::text, tt.start_time) AS week, p.name AS project, a.name AS activity, sum((date_part('epoch'::text, interval_round_up((tt.end_time - tt.start_time), '00:15:00'::interval)) / (3600)::double precision)) AS duration, u.name FROM projects p, tasks t, task_time tt, users u, activities a WHERE ((((((p.id = t.project_id) AND (t.id = tt.task_id)) AND (tt.user_id = u.id)) AND (tt.start_time > '2006-12-01 00:00:00'::timestamp without time zone)) AND (a.id = t.activity_id)) AND (p.name = 'LShift'::text)) GROUP BY date_part('year'::text, tt.start_time), date_part('week'::text, tt.start_time), p.name, a.name, u.name ORDER BY date_part('year'::text, tt.start_time) DESC, date_part('week'::text, tt.start_time) DESC, p.name, a.name, u.name;


ALTER TABLE public.excel_report_2_lshifttime OWNER TO timetracker;

--
-- Name: excel_report_by_month; Type: VIEW; Schema: public; Owner: timetracker
--

CREATE VIEW excel_report_by_month AS
    SELECT date_part('year'::text, tt.start_time) AS year, date_part('month'::text, tt.start_time) AS month, p.name AS project, sum((date_part('epoch'::text, interval_round_up((tt.end_time - tt.start_time), '00:15:00'::interval)) / (3600)::double precision)) AS duration, u.name FROM projects p, tasks t, task_time tt, users u WHERE ((((p.id = t.project_id) AND (t.id = tt.task_id)) AND (tt.user_id = u.id)) AND (tt.start_time > '2008-01-01 00:00:00'::timestamp without time zone)) GROUP BY date_part('year'::text, tt.start_time), date_part('month'::text, tt.start_time), p.name, u.name ORDER BY date_part('year'::text, tt.start_time) DESC, date_part('month'::text, tt.start_time) DESC, p.name, u.name;


ALTER TABLE public.excel_report_by_month OWNER TO timetracker;

--
-- Name: excel_report_by_week; Type: VIEW; Schema: public; Owner: timetracker
--

CREATE VIEW excel_report_by_week AS
    SELECT date_part('year'::text, tt.start_time) AS year, date_part('week'::text, tt.start_time) AS week, p.name AS project, sum((date_part('epoch'::text, interval_round_up((tt.end_time - tt.start_time), '00:15:00'::interval)) / (3600)::double precision)) AS duration, u.name FROM projects p, tasks t, task_time tt, users u WHERE ((((p.id = t.project_id) AND (t.id = tt.task_id)) AND (tt.user_id = u.id)) AND (tt.start_time > '2008-01-01 00:00:00'::timestamp without time zone)) GROUP BY date_part('year'::text, tt.start_time), date_part('week'::text, tt.start_time), p.name, u.name ORDER BY date_part('year'::text, tt.start_time) DESC, date_part('week'::text, tt.start_time) DESC, p.name, u.name;


ALTER TABLE public.excel_report_by_week OWNER TO timetracker;

--
-- Name: granularity_options; Type: TABLE; Schema: public; Owner: timetracker; Tablespace: 
--

CREATE TABLE granularity_options (
    interval_value interval
);


ALTER TABLE public.granularity_options OWNER TO timetracker;

--
-- Name: projects_id_seq; Type: SEQUENCE; Schema: public; Owner: timetracker
--

CREATE SEQUENCE projects_id_seq
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;


ALTER TABLE public.projects_id_seq OWNER TO timetracker;

--
-- Name: projects_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: timetracker
--

ALTER SEQUENCE projects_id_seq OWNED BY projects.id;


--
-- Name: rounded_up_time; Type: VIEW; Schema: public; Owner: timetracker
--

CREATE VIEW rounded_up_time AS
    SELECT u.name AS user_name, p.name AS project_name, a.name AS activity_name, t.bug AS bug_id, t.description, date_part('year'::text, tt.start_time) AS year, date_part('quarter'::text, tt.start_time) AS quarter, date_part('month'::text, tt.start_time) AS month, date_part('week'::text, tt.start_time) AS week, date_part('day'::text, tt.start_time) AS day, date_part('hour'::text, tt.start_time) AS hour, date_part('minute'::text, tt.start_time) AS minute, tt.start_time, tt.end_time, (tt.end_time - tt.start_time) AS raw_duration, (date_part('epoch'::text, interval_round_up((tt.end_time - tt.start_time), gran.interval_value)) / (3600)::double precision) AS rounded_duration_hours, gran.interval_value FROM users u, projects p, tasks t, task_time tt, activities a, granularity_options gran WHERE ((((u.id = tt.user_id) AND (p.id = t.project_id)) AND (t.id = tt.task_id)) AND (t.activity_id = a.id));


ALTER TABLE public.rounded_up_time OWNER TO timetracker;

--
-- Name: task_time_id_seq; Type: SEQUENCE; Schema: public; Owner: timetracker
--

CREATE SEQUENCE task_time_id_seq
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;


ALTER TABLE public.task_time_id_seq OWNER TO timetracker;

--
-- Name: task_time_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: timetracker
--

ALTER SEQUENCE task_time_id_seq OWNED BY task_time.id;


--
-- Name: tasks_id_seq; Type: SEQUENCE; Schema: public; Owner: timetracker
--

CREATE SEQUENCE tasks_id_seq
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;


ALTER TABLE public.tasks_id_seq OWNER TO timetracker;

--
-- Name: tasks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: timetracker
--

ALTER SEQUENCE tasks_id_seq OWNED BY tasks.id;


--
-- Name: timesheet_by_user; Type: VIEW; Schema: public; Owner: timetracker
--

CREATE VIEW timesheet_by_user AS
    SELECT u.name AS user_name, p.name AS project, (tt.start_time)::date AS date, interval_round_up((tt.end_time - tt.start_time), '00:15:00'::interval) AS duration, replace(a.name, 'Not set'::text, ''::text) AS activity, t.bug, t.description FROM projects p, tasks t, task_time tt, activities a, users u WHERE ((((p.id = t.project_id) AND (t.id = tt.task_id)) AND (t.activity_id = a.id)) AND (u.id = tt.user_id)) ORDER BY p.name, (tt.start_time)::date;


ALTER TABLE public.timesheet_by_user OWNER TO timetracker;

--
-- Name: timesheet_to_15_minutes; Type: VIEW; Schema: public; Owner: timetracker
--

CREATE VIEW timesheet_to_15_minutes AS
    SELECT p.name AS project, (tt.start_time)::date AS date, interval_round_up((tt.end_time - tt.start_time), '00:15:00'::interval) AS duration, replace(a.name, 'Not set'::text, ''::text) AS activity, t.bug, t.description FROM projects p, tasks t, task_time tt, activities a WHERE (((p.id = t.project_id) AND (t.id = tt.task_id)) AND (t.activity_id = a.id)) ORDER BY p.name, (tt.start_time)::date;


ALTER TABLE public.timesheet_to_15_minutes OWNER TO timetracker;

--
-- Name: trac_projects; Type: TABLE; Schema: public; Owner: timetracker; Tablespace: 
--

CREATE TABLE trac_projects (
    id integer NOT NULL,
    url text NOT NULL,
    username text NOT NULL,
    password text NOT NULL,
    author text NOT NULL
);


ALTER TABLE public.trac_projects OWNER TO timetracker;

--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: timetracker
--

CREATE SEQUENCE users_id_seq
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;


ALTER TABLE public.users_id_seq OWNER TO timetracker;

--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: timetracker
--

ALTER SEQUENCE users_id_seq OWNED BY users.id;


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: timetracker
--

ALTER TABLE activities ALTER COLUMN id SET DEFAULT nextval('activities_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: timetracker
--

ALTER TABLE projects ALTER COLUMN id SET DEFAULT nextval('projects_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: timetracker
--

ALTER TABLE task_time ALTER COLUMN id SET DEFAULT nextval('task_time_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: timetracker
--

ALTER TABLE tasks ALTER COLUMN id SET DEFAULT nextval('tasks_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: timetracker
--

ALTER TABLE users ALTER COLUMN id SET DEFAULT nextval('users_id_seq'::regclass);


--
-- Name: activities_name_key; Type: CONSTRAINT; Schema: public; Owner: timetracker; Tablespace: 
--

ALTER TABLE ONLY activities
    ADD CONSTRAINT activities_name_key UNIQUE (name);


--
-- Name: activities_pkey; Type: CONSTRAINT; Schema: public; Owner: timetracker; Tablespace: 
--

ALTER TABLE ONLY activities
    ADD CONSTRAINT activities_pkey PRIMARY KEY (id);


--
-- Name: projects_name_key; Type: CONSTRAINT; Schema: public; Owner: timetracker; Tablespace: 
--

ALTER TABLE ONLY projects
    ADD CONSTRAINT projects_name_key UNIQUE (name);


--
-- Name: projects_pkey; Type: CONSTRAINT; Schema: public; Owner: timetracker; Tablespace: 
--

ALTER TABLE ONLY projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: task_time_pkey; Type: CONSTRAINT; Schema: public; Owner: timetracker; Tablespace: 
--

ALTER TABLE ONLY task_time
    ADD CONSTRAINT task_time_pkey PRIMARY KEY (id);


--
-- Name: tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: timetracker; Tablespace: 
--

ALTER TABLE ONLY tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);


--
-- Name: trac_projects_id_key; Type: CONSTRAINT; Schema: public; Owner: timetracker; Tablespace: 
--

ALTER TABLE ONLY trac_projects
    ADD CONSTRAINT trac_projects_id_key UNIQUE (id);


--
-- Name: users_name_key; Type: CONSTRAINT; Schema: public; Owner: timetracker; Tablespace: 
--

ALTER TABLE ONLY users
    ADD CONSTRAINT users_name_key UNIQUE (name);


--
-- Name: users_pkey; Type: CONSTRAINT; Schema: public; Owner: timetracker; Tablespace: 
--

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_endtime; Type: INDEX; Schema: public; Owner: timetracker; Tablespace: 
--

CREATE INDEX idx_endtime ON task_time USING btree (end_time);


--
-- Name: idx_sttime; Type: INDEX; Schema: public; Owner: timetracker; Tablespace: 
--

CREATE INDEX idx_sttime ON task_time USING btree (start_time);


--
-- Name: task_time_ix; Type: INDEX; Schema: public; Owner: timetracker; Tablespace: 
--

CREATE INDEX task_time_ix ON task_time USING btree (user_id, task_id);


--
-- Name: $1; Type: FK CONSTRAINT; Schema: public; Owner: timetracker
--

ALTER TABLE ONLY tasks
    ADD CONSTRAINT "$1" FOREIGN KEY (project_id) REFERENCES projects(id);


--
-- Name: $1; Type: FK CONSTRAINT; Schema: public; Owner: timetracker
--

ALTER TABLE ONLY task_time
    ADD CONSTRAINT "$1" FOREIGN KEY (user_id) REFERENCES users(id);


--
-- Name: $2; Type: FK CONSTRAINT; Schema: public; Owner: timetracker
--

ALTER TABLE ONLY tasks
    ADD CONSTRAINT "$2" FOREIGN KEY (activity_id) REFERENCES activities(id);


--
-- Name: $2; Type: FK CONSTRAINT; Schema: public; Owner: timetracker
--

ALTER TABLE ONLY task_time
    ADD CONSTRAINT "$2" FOREIGN KEY (task_id) REFERENCES tasks(id);


--
-- Name: trac_projects_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: timetracker
--

ALTER TABLE ONLY trac_projects
    ADD CONSTRAINT trac_projects_id_fkey FOREIGN KEY (id) REFERENCES projects(id);

