from collections import OrderedDict
import yaml
from enum import Enum

class Services:
    recorded_ports = []
    expose_ports = []

    @staticmethod
    def set_expose_ports(ports):
        Services.expose_ports = ports

    @staticmethod
    def should_expose_port(name):
        Services.recorded_ports.append(name)
        return Services.expose_ports.has_key(name)

    @staticmethod
    def get_port(name, default):
        port = Services.expose_ports.get(name, None)
        if port == None:
            return default
        else:
            return port

    @staticmethod
    def valid_ports():
        return Services.recorded_ports

    @staticmethod
    def invalid_ports():
        valid_ports = set(Services.recorded_ports)
        provided_ports = set(Services.expose_ports)
        return provided_ports.difference(valid_ports)

class Postgres:
    def __init__(self, password="mysecretpassword", seeded=False, initial_files = []):
        self.password = password
        self.seeded = seeded
        self.name = "postgres"
        self.initial_files = initial_files
    def service(self):
        ret = OrderedDict()
        if self.seeded:
            ret["build"] = {
                "context": ".",
                "dockerfile": "etc/Dockerfile-seeded-postgres"
            }
        else:
            ret["image"] = "postgres:9.5"
        if Services.should_expose_port("postgres"):
            port = Services.get_port("postgres", 5432)
            ret["ports"] = ["%d:5432"%port]
        ret["environment"] = {"POSTGRES_PASSWORD":self.password}
        if len(self.initial_files) > 0:
            ret["volumes"] = ["%s:/docker-entrypoint-initdb.d/%s" %(x, x.split("/")[-1]) for x in self.initial_files]
        return ret
    def env(self):
        return {
            "DATABASE_URL": "postgres://postgres:%s@%s/postgres" % (self.password, self.name),
            "PGPASSWORD": self.password
        }

class ExternalPostgres:
    def __init__(self, host, db, username, password):
        self.host = host
        self.db = db
        self.username = username
        self.password = password
    def env(self):
        return {
            "DATABASE_URL": "postgres://%s:%s@%s/%s" % (self.username, self.password, self.host, self.db),
            "PGPASSWORD": self.password
        }

class TimetrackerType(Enum):
    PreBuilt = 1
    Tester = 2
    Dev = 3

class Timetracker:
    WEB_INTERNAL_PORT = 18000
    NREPL_INTERNAL_PORT = 18001
    def __init__(self, postgres, kind, image_tag=None):
        self.name = "timetracker-app"
        self.postgres = postgres
        self.kind = kind
        self.image_tag = image_tag
    def service(self):
        ret = OrderedDict()
        ret["environment"] = self.postgres.env()
        ports = []
        if self.kind == TimetrackerType.PreBuilt:
            ret["image"] = "lshift/timetracker-web:%s" % self.image_tag
            run_command = "lein run"
        elif self.kind == TimetrackerType.Tester:
            ret["build"] = "."
            run_command = "psql --host=%s --username=postgres -f db/users.sql && lein with-profile +teamcity run" % self.postgres.name
        elif self.kind == TimetrackerType.Dev:
            ret["build"] = "."
            ret["volumes"] = [".:/usr/src/app/"]
            run_command = "psql --host=%s --username=postgres -f db/users.sql && lein with-profile +dev run" % self.postgres.name

        if Services.should_expose_port("timetracker-web"):
            port = Services.get_port("timetracker-web", 80)
            ports.append("%d:%d"%(port, self.WEB_INTERNAL_PORT))
        if Services.should_expose_port("timetracker-nrepl"):
            port = Services.get_port("timetracker-nrepl", self.NREPL_INTERNAL_PORT)
            ret["environment"]["NREPL_PORT"] = self.NREPL_INTERNAL_PORT
            ports.append("%d:%d"%(port, self.NREPL_INTERNAL_PORT))
        if len(ports) > 0:
            ret["ports"] = ports

        if self.postgres.__class__ == ExternalPostgres:
            ret["command"] = run_command
        else:
            ret["command"] = "bash -c \"./etc/wait-for-postgres.sh %s 5432 && %s\"" % (self.postgres.name, run_command)
            ret["links"] = [self.postgres.name]
        return ret
    def link(self):
        return "http://%s:%d/" % (self.name, Services.get_port("timetracker-web", self.WEB_INTERNAL_PORT))

class Selenium:
    def __init__(self, timetracker):
        self.name = "selenium"
        self.timetracker = timetracker
    def service(self):
        ret = OrderedDict()
        ret["image"] = "selenium/standalone-firefox-debug:2.52.0"
        ret["links"] = [self.timetracker.name]
        ports = []
        if Services.should_expose_port("selenium-driver"):
            port = Services.get_port("selenium-driver", 4444)
            ports.append("%d:4444"%port)
        if Services.should_expose_port("selenium-vnc"):
            port = Services.get_port("selenium-vnc", 5900)
            ports.append("%d:5900"%port)
        if len(ports) > 0:
            ret["ports"] = ports
        return ret
    def link(self):
        return "http://%s:4444/wd/hub" % self.name

# We run two instances of timetracker, one to run the tests and one to act as
# the server. This is done to prevent a circular reference to the selenium
# instance.
class TimetrackerTest:
    NREPL_INTERNAL_PORT = 18001
    def __init__(self, timetracker, selenium, postgres, kind=TimetrackerType.Tester):
        self.timetracker = timetracker
        self.selenium = selenium
        self.postgres = postgres
        self.name = "timetracker-test"
        self.kind = kind
    def service(self):
        ret = OrderedDict()
        ret["build"] = "."
        if self.kind == TimetrackerType.Tester:
            ret["command"] = "bash -c \"lein with-profile +teamcity do doo node test once, test\""
            ret["volumes"] = [
                "./test-results:/usr/src/app/test-results",
                "./target/screenshots:/usr/src/app/target/screenshots"
            ]
        elif self.kind == TimetrackerType.Dev:
            ret["command"] = "bash -c \"./etc/wait-for-postgres.sh %s 5432 && lein with-profile +dev run\"" % self.postgres.name
            ret["volumes"] = [".:/usr/src/app/"]
        else:
            raise Exception, "TimetrackerTest does not support %s" %self.kind
        ret["links"] = [self.timetracker.name, self.selenium.name, self.postgres.name]
        ret["environment"] = self.postgres.env()
        ret["environment"]["TIMETRACKER_APP_URL"] = self.timetracker.link()
        ret["environment"]["TIMETRACKER_SERVER_URL"] = self.timetracker.link()
        ret["environment"]["SELENIUM_URL"] = self.selenium.link()
        ports = []
        if Services.should_expose_port("test-nrepl"):
            port = Services.get_port("test-nrepl", self.NREPL_INTERNAL_PORT)
            ret["environment"]["NREPL_PORT"] = self.NREPL_INTERNAL_PORT
            ports.append("%d:%d"%(port, self.NREPL_INTERNAL_PORT))
        if len(ports) > 0:
            ret["ports"] = ports
        return ret

class ClojureService(object):
    def service(self, command):
        ret = OrderedDict()
        ret["build"] = {
            "context": ".",
            "dockerfile": "dev/Dockerfile-clojure-deps"
        }
        ret["volumes"] = [".:/usr/src/app/"]
        return ret

class Figwheel(ClojureService):
    def __init__(self):
        self.name = "figwheel"
    def service(self):
        ret = super(Figwheel, self).service(self)
        ret["ports"] = ["3449:3449"]
        ret["command"] = "bash -c \"cd /usr/src/app/ && lein figwheel\""
        return ret

class ClojureReformat(ClojureService):
    def __init__(self):
        self.name = "clojure-reformat"
    def service(self):
        ret = super(ClojureReformat, self).service(self)
        ret["command"] = "lein auto do cljfmt fix, cljfmt fix project.clj"
        return ret

# from http://blog.elsdoerfer.name/2012/07/26/make-pyyaml-output-an-ordereddict/
def represent_odict(dump, tag, mapping, flow_style=None):
    """Like BaseRepresenter.represent_mapping, but does not issue the sort().
    """
    value = []
    node = yaml.MappingNode(tag, value, flow_style=flow_style)
    if dump.alias_key is not None:
        dump.represented_objects[dump.alias_key] = node
    best_style = True
    if hasattr(mapping, 'items'):
        mapping = mapping.items()
    for item_key, item_value in mapping:
        node_key = dump.represent_data(item_key)
        node_value = dump.represent_data(item_value)
        if not (isinstance(node_key, yaml.ScalarNode) and not node_key.style):
            best_style = False
        if not (isinstance(node_value, yaml.ScalarNode) and not node_value.style):
            best_style = False
        value.append((node_key, node_value))
    if flow_style is None:
        if dump.default_flow_style is not None:
            node.flow_style = dump.default_flow_style
        else:
            node.flow_style = best_style
    return node
