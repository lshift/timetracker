FROM clojure:lein-2.7.1-alpine
RUN apk update && apk add postgresql-client bzip2 wget git nodejs
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
# This project.clj & lein deps is done so that Docker caches the state after
# dependencies but before there are potential  code changes in the COPY below
COPY project.clj /usr/src/app/
RUN lein deps

RUN mkdir etc
COPY etc/check-deps.sh ./etc/check-deps.sh
RUN ./etc/check-deps.sh

COPY . /usr/src/app

RUN lein cljsbuild once prod

CMD ["lein", "run"]
