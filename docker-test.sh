#!/bin/bash

set -e
set -v

# Expected env vars for TeamCity to deploy below. If they're not present, it
# will not be pushed to Docker Hub.
# BRANCH - git branch
# COMMIT - git commit
# DOCKER_HUB_USER / DOCKER_HUB_PASS - authentication details for Docker Hub

export COMPOSE_PROJECT_NAME=timetrackerweb

if [ "$COMMIT" = "" ]; then
    echo -n "unknown" > resources/timetracker-version
else
    build=${COMMIT:0:8}
    echo -n $build > resources/timetracker-version
fi

export PATH=$PATH:~/.local/bin
set +e
existing_pip=$(which pip)
set -e
if [ "x$existing_pip" == "x" ]; then
    # Last static version of get-pip before it got put in a separate repository
    wget https://raw.githubusercontent.com/pypa/pip/2dcb0a3610719ce1e88bac700dda9b9e60ee9299/contrib/get-pip.py
    python get-pip.py --user
fi
pip install --user -r requirements.txt

# Make sure clojure-reformat still works
find . -path ./target -prune -o -path "*/*/*.clj*" -type f -print |sort | xargs cat > originals

python generate-compose.py base > docker-compose.yml
docker-compose up -d --force-recreate clojure-reformat
sleep 10
set +e
docker logs timetrackerweb_clojure-reformat_1 2>/dev/null |grep Exception:
if [ "x$?" == "x0" ]; then
    docker logs timetrackerweb_clojure-reformat_1
    exit 1
fi
set -e

# .. and check no reformatting has happened
find . -path ./target -prune -o -path "*/*/*.clj*" -type f -print |sort | xargs cat > formatted
diff --unified originals formatted

python generate-compose.py testing > docker-compose.yml
docker-compose build
docker-compose kill
docker-compose rm -f
docker-compose up --abort-on-container-exit | tee docker-out

status=$(grep -oP '(?<='$COMPOSE_PROJECT_NAME'_timetracker-test_1 exited with code )[0-9]+' docker-out)
if [ "$status" -ne "0" ]; then
    echo "Aborting with status $status"
    exit $status
fi

if [ "$DOCKER_HUB_USER" = "" -o "$DOCKER_HUB_PASS" = "" ]; then
    echo "DOCKER_HUB_USER and DOCKER_HUB_PASS are not set. Not publishing";
    exit
fi

# Chop refs/heads/ off to get 'master', etc.
branch=$(perl -e 'print $ARGV[0] =~ s/.*\///r' "$BRANCH")
if [ "$branch" = "master" -a "$COMMIT" != "" ]; then
    docker login --username "$DOCKER_HUB_USER" --password "$DOCKER_HUB_PASS"

    tag="$branch-$COMMIT"
    docker tag ${COMPOSE_PROJECT_NAME}_timetracker-app:latest "lshift/timetracker-web:$tag"
    docker push "lshift/timetracker-web:$tag"

    docker logout
else
    echo "COMMIT not set or not on BRANCH master. Not publishing"
    exit
fi
