Timetracker
===========

Timetracker is an app used internally by LShift to track our time usage.

Quick start
-----------
1. Install [Docker](https://www.docker.com/) and [Docker compose](https://docs.docker.com/compose/install/)
2. Run `pip install -r requirements.txt`
3. Run `python generate-compose.py --expose-ports timetracker-nrepl,timetracker-web:18000 > docker-compose.yml`
4. Run `docker-compose up -d`
5. Run `docker-compose logs timetracker`and wait until you see `t.main - :time-tracker.main/running`
6. http://localhost:18000/ should now have a running instance of Timetracker
7. localhost:18001 will be running an nREPL which can be connected to via `lein
   repl :connect 18001` or with a suitable editor plugin (or 18002 for the Selenium test Timetracker instance)

**N.B.** ClojureScript source code will be recompiled and reloaded in the
browser on every save. Clojure source code will *not* be automatically required
as it is saved. However, you can evaluate forms or require whole namespaces via
the REPL.

Slightly-less quick start
-------------------------
This way you get running processes outside of Docker, which is a little easier to debug

1. Install [Docker](https://www.docker.com/) and [Leiningen](https://leiningen.org/)
2. Run `pip install -r requirements.txt`
3. Run `python generate-compose.py --expose-port postgres > docker-compose.yml`
4. Run `docker-compose up -d postgres` (gets you a Postgres DB)
5. Run `lein figwheel`, and leave this running (compiles the Clojurescript code)
6. In a new terminal, run `DATABASE_URL=postgres://postgres:mysecretpassword@localhost/postgres lein with-profile +dev repl` which will bring up a repl with the prompt `user=>`
7. In the repl, type `(user/reset)` and wait for it to say `:ready`
8. http://localhost:18000/ should now have a running instance of Timetracker
9. If you need an admin user, run `DATABASE_URL=[as above] python manage.py createsuperuser` in admin

Running test system with Docker
-------------------------------

All tests (unit and integration) for Timetracker can be run via Docker by
calling `docker-test.sh`.

License
-------

MIT. See LICENSE

Contributors
------------

* Ceri Storey
* Tom Parker
* Neil Kirsopp
* Ashley Hewson
* Paul Jones
* Peter Rolph
* Andrew Martin

