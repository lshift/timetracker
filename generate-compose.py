import yaml
from collections import OrderedDict
from etc.services import *
import argparse

class SplitAction(argparse.Action):
  def __call__(self, parser, namespace, values, option_string=None):
    ports = getattr(namespace, self.dest)
    items = values.split(",")
    for item in items:
        if item.find(":") != -1:
            (name, port) = item.split(":")
            ports[name] = int(port)
        else:
            ports[item] = None
    setattr(namespace, self.dest, ports)

parser = argparse.ArgumentParser()
parser.add_argument('compose_type', choices=['testing', 'trial', 'base', "production"], default="base", help="Type of compose file to output", nargs='?')
parser.add_argument('--image-tag', default="master-test", help="In 'trial' mode, tag of prebuilt Timetracker image (default: master-test)")
parser.add_argument('--expose-ports', default={}, action=SplitAction, help="Comma separated port mappings of the form SERVICE or SERVICE:PORT. SERVICE form gives default port")
parser.add_argument("--host", help="DB host (only relevant for production)")
parser.add_argument("--db", help="DB to use (only relevant for production)")
parser.add_argument("--username", help="DB username to use (only relevant for production)")
parser.add_argument("--password", help="DB password to use (only relevant for production)")
args = parser.parse_args()
Services.set_expose_ports(args.expose_ports)

compose = OrderedDict()
compose["version"] = "2"
compose["services"] = OrderedDict()

services = []
if args.compose_type == "testing":
    postgres = Postgres()
    timetracker = Timetracker(postgres, TimetrackerType.Tester)
    selenium = Selenium(timetracker)
    test = TimetrackerTest(timetracker, selenium, postgres)
    services = [timetracker, test, selenium, postgres]
elif args.compose_type == "trial":
    postgres = Postgres(seeded=True)
    timetracker = Timetracker(postgres, TimetrackerType.PreBuilt, image_tag=args.image_tag)
    services = [timetracker, postgres]
elif args.compose_type == "base":
    postgres = Postgres(initial_files=["./db/users.sql"])
    timetracker = Timetracker(postgres, TimetrackerType.Dev)
    figwheel = Figwheel()
    reformat = ClojureReformat()
    selenium = Selenium(timetracker)
    # TODO hack for quick start to work - this is competing with timetracker
    # above to migrate the DB
    # test = TimetrackerTest(timetracker, selenium, postgres, kind=TimetrackerType.Dev)
    services = [timetracker, figwheel, reformat, postgres, selenium]
elif args.compose_type == "production":
    if args.host == None:
        parser.error("Need a host")
    if args.db == None:
        parser.error("Need a DB")
    if args.username == None:
        parser.error("Need a DB username")
    if args.password == None:
        parser.error("Need a DB password")
    postgres = ExternalPostgres(args.host, args.db, args.username, args.password)
    timetracker = Timetracker(postgres, TimetrackerType.PreBuilt, image_tag=args.image_tag)
    services = [timetracker]
else:
    parser.error("Don't know compose type %s"% args.compose_type)

for s in services:
    compose["services"][s.name] = s.service()

invalid_ports = Services.invalid_ports()
if len(invalid_ports) > 0:
    parser.error("Invalid port mappings: %s. Only valid ports for this config are %s" % (", ".join(invalid_ports), ", ".join(sorted(Services.valid_ports()))))

yaml.SafeDumper.add_representer(OrderedDict,
    lambda dumper, value: represent_odict(dumper, u'tag:yaml.org,2002:map', value))
print yaml.safe_dump(compose, default_flow_style=False, width=1000)
