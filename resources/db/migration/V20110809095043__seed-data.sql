--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

SET search_path = public, pg_catalog;

--
-- Data for Name: activities; Type: TABLE DATA; Schema: public; Owner: timetracker
--

INSERT INTO activities VALUES (1, 'Not set', true);
INSERT INTO activities VALUES (2, 'Admin', true);
INSERT INTO activities VALUES (4, 'Client Meeting', true);
INSERT INTO activities VALUES (5, 'Deployment', true);
INSERT INTO activities VALUES (6, 'Design', true);
INSERT INTO activities VALUES (7, 'Development', true);
INSERT INTO activities VALUES (8, 'Documentation', true);
INSERT INTO activities VALUES (10, 'Internal Meeting', true);
INSERT INTO activities VALUES (12, 'QA', true);
INSERT INTO activities VALUES (13, 'Research', true);
INSERT INTO activities VALUES (14, 'Specification', true);
INSERT INTO activities VALUES (3, 'Business Development', true);
INSERT INTO activities VALUES (18, 'Travel', true);
INSERT INTO activities VALUES (19, 'Marketing', true);
INSERT INTO activities VALUES (20, 'Human Resources', true);
INSERT INTO activities VALUES (23, 'Project Management', true);
INSERT INTO activities VALUES (24, 'Training', true);
INSERT INTO activities VALUES (27, 'Sys Admin', true);
INSERT INTO activities VALUES (9, 'Finance Admin', false);
INSERT INTO activities VALUES (21, 'Recruitment', false);
INSERT INTO activities VALUES (11, 'New Business', false);
INSERT INTO activities VALUES (17, 'Testing', false);
INSERT INTO activities VALUES (16, 'Systems', false);
INSERT INTO activities VALUES (26, 'Warranty', false);
INSERT INTO activities VALUES (22, 'Consultancy', true);
INSERT INTO activities VALUES (25, 'Investigation', true);
INSERT INTO activities VALUES (28, 'Estimation', true);
INSERT INTO activities VALUES (15, 'Support', true);
INSERT INTO activities VALUES (31, 'Undefined', true);


--
-- Name: activities_id_seq; Type: SEQUENCE SET; Schema: public; Owner: timetracker
--

SELECT pg_catalog.setval('activities_id_seq', 31, true);


--
-- Data for Name: granularity_options; Type: TABLE DATA; Schema: public; Owner: timetracker
--

INSERT INTO granularity_options VALUES ('00:00:00');
INSERT INTO granularity_options VALUES ('00:05:00');
INSERT INTO granularity_options VALUES ('00:10:00');
INSERT INTO granularity_options VALUES ('00:15:00');
INSERT INTO granularity_options VALUES ('00:30:00');
INSERT INTO granularity_options VALUES ('02:00:00');
INSERT INTO granularity_options VALUES ('03:00:00');
INSERT INTO granularity_options VALUES ('04:00:00');
INSERT INTO granularity_options VALUES ('06:00:00');
INSERT INTO granularity_options VALUES ('08:00:00');
INSERT INTO granularity_options VALUES ('10:00:00');
INSERT INTO granularity_options VALUES ('12:00:00');


--
-- Data for Name: projects; Type: TABLE DATA; Schema: public; Owner: timetracker
--

INSERT INTO projects VALUES (3, 'Foo', false);
INSERT INTO projects VALUES (6, 'Bar', false);
INSERT INTO projects VALUES (7, 'Baz', false);
INSERT INTO projects VALUES (8, 'Quux', false);
INSERT INTO projects VALUES (1, 'Not set', true);
INSERT INTO projects VALUES (32, 'Habitat Support', true);
INSERT INTO projects VALUES (35, 'Holiday Personal', true);
INSERT INTO projects VALUES (36, 'Holiday Bank', true);
INSERT INTO projects VALUES (38, 'LShift', true);
INSERT INTO projects VALUES (50, 'Sick', true);
INSERT INTO projects VALUES (64, 'LShift R&D', true);
INSERT INTO projects VALUES (187, 'LShift SysAdmin', true);


--
-- Name: projects_id_seq; Type: SEQUENCE SET; Schema: public; Owner: timetracker
--

SELECT pg_catalog.setval('projects_id_seq', 205, true);


--
-- Data for Name: tasks; Type: TABLE DATA; Schema: public; Owner: timetracker
--

INSERT INTO tasks VALUES (1, 38, 7, 'XY-23', 'Artichokes');
INSERT INTO tasks VALUES (2, 38, 25, 'XY-42', 'Stuff');


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: timetracker
--

INSERT INTO users VALUES (49, 'paulj', true);
INSERT INTO users VALUES (59, 'Michal', true);
INSERT INTO users VALUES (41, 'Alice', true);
INSERT INTO users VALUES (35, 'Ben', false);
INSERT INTO users VALUES (42, 'Charlie', true);
INSERT INTO users VALUES (43, 'Dave', true);
INSERT INTO users VALUES (44, 'Eric', true);
INSERT INTO users VALUES (45, 'Frank', true);


--
-- Data for Name: task_time; Type: TABLE DATA; Schema: public; Owner: timetracker
--


--
-- Name: task_time_id_seq; Type: SEQUENCE SET; Schema: public; Owner: timetracker
--

SELECT pg_catalog.setval('task_time_id_seq', 8, true);


--
-- Name: tasks_id_seq; Type: SEQUENCE SET; Schema: public; Owner: timetracker
--

SELECT pg_catalog.setval('tasks_id_seq', 2, true);


--
-- Data for Name: trac_projects; Type: TABLE DATA; Schema: public; Owner: timetracker
--



--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: timetracker
--

SELECT pg_catalog.setval('users_id_seq', 64, true);


--
-- PostgreSQL database dump complete
--

