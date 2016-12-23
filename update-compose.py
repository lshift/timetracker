import requests
import json
import datetime
import docker
import subprocess
import os
import sys

kind = sys.argv[1]
cmd = ["python", "generate-compose.py"]

if kind == "production":
    cmd.extend([
        "--host", os.environ["TT_HOST"],
        "--db", os.environ["TT_DB"],
        "--username", os.environ["TT_USERNAME"],
        "--password", os.environ["TT_PASSWORD"],
        "production"])
elif kind == "trial":
    cmd.append("trial")
else:
    raise Exception, "Don't know %s as system option" %kind
cmd.extend(["--expose-ports", "timetracker-web:18000"])

token = open("docker-hub-token").read()
headers = {
    "Content-Type": "application/json",
    "Authorization": "JWT %s" % token
}

def last_updated(item):
    return datetime.datetime.strptime(item["last_updated"], "%Y-%m-%dT%H:%M:%S.%fZ")

client = docker.Client()

images_req = requests.get("https://hub.docker.com/v2/repositories/lshift/timetracker-web/tags?page_size=10000", headers=headers)
images = images_req.json()["results"]
images = sorted(images, key=last_updated, reverse=True)
image = images[0]
container_name = "timetracker_timetracker-app_1"
postgres_container_name = "timetracker_postgres_1"
try:
    container = client.inspect_container(container_name)
    current_image = container["Config"]["Image"]
    wanted_name = "lshift/timetracker-web:%s" % image["name"]
    if current_image == wanted_name:
        if not container["State"]["Running"]:
            print "%s exists, but isn't running so need to fix that" % container_name
        else:
            print "Already running %s" % current_image
            if kind == "trial":
                try:
                    container = client.inspect_container(postgres_container_name)
                    if not container["State"]["Running"]:
                        print "%s exists, but isn't running so need to fix that" % postgres_container_name
                    else:
                        print "And have postgres container (%s) up as well" % postgres_container_name
                        exit(0)
                except docker.errors.NotFound:
                    print "Missing Postgres container, so need to rebuild"
            else:
                exit(0)
    else:
        print "%s != %s, so need to recreate" % (current_image, wanted_name)
except docker.errors.NotFound:
    print "Don't have %s yet, so creating" % container_name
cmd.extend(["--image-tag", image["name"]])

compose = subprocess.check_output(cmd)
open("docker-compose.yml", "w").write(compose)
subprocess.check_call(["docker-compose", "--project-name", "timetracker", "up", "-d"])
