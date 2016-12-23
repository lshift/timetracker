(ns time-tracker.environ
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [time-tracker.db-env :refer [db-from-env]]
            [time-tracker.graphs-data]
            [time-tracker.jetty]
            [time-tracker.migrator]
            [time-tracker.missed-timesheets]
            [time-tracker.postgresql]
            [time-tracker.ring-composite]
            [time-tracker.time-service]
            [time-tracker.timezilla]))

(defn core-system [db-locator]
  (component/system-map
   :db         (time-tracker.postgresql/postgresql db-locator)
   :migrator   (component/using
                (time-tracker.migrator/instance)
                [:db])
   :time-app   (component/using
                (time-tracker.time-service/instance)
                [:db :migrator])
   :tardy-timesheets
   (component/using
    (time-tracker.missed-timesheets/instance)
    [:db :migrator])
   :timezilla  (component/using
                (time-tracker.timezilla/instance)
                {:db :db})
   :graphs     (component/using
                (time-tracker.graphs-data/instance)
                [:db :migrator])
   :web-app    (component/using
                (time-tracker.ring-composite/overlay [:time-app :tardy-timesheets :timezilla :graphs])
                [:tardy-timesheets :time-app :migrator :timezilla :graphs])))

(defn with-web-server
  "Returns an instance of the complete running system"
  [{:keys [port db-locator] :as config}]
  (merge
   (core-system db-locator)
   (component/system-map
    :web-server (component/using
                 (time-tracker.jetty/jetty-server port)
                 [:web-app]))))

(defn int-opt
  [n]
  (when n
    (Integer/parseInt n)))

(defn system []
  (let [env-config {:port       (or (int-opt (:timetracker-port env)) 18000)
                    :db-locator (db-from-env)}]
    (with-web-server env-config)))
