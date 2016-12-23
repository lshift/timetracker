#!/bin/bash

host="$1"
port="$2"

until psql -q --host=$host --port=$port --username=postgres -d template1 -c 'select 1;' ; do
  >&2 echo "$host:$port is unavailable - sleeping"
  sleep 1
done

>&2 echo "$host:$port is up"
