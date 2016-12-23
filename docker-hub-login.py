import requests
import json
from sys import argv, exit

if len(argv) != 3:
    print "Expected two arguments: username and password for Docker hub"
    exit(-1)

headers = {"Content-Type": "application/json"}
login = requests.post("https://hub.docker.com/v2/users/login/", data=json.dumps({"username":argv[1], "password":argv[2]}), headers=headers)
if login.status_code != 200:
    raise Exception, (login.status_code, login.json())
token = login.json()[u"token"]
open("docker-hub-token", "w").write(token)
print "Wrote token to docker-hub-token"
