(ns time-tracker.postgresql
  (:require  [com.stuartsierra.component :as component]
             [clj-bonecp-url.core :as bonecp]
             [clojure.tools.logging :as log]
             [clojure.set :refer [map-invert]]))

(defrecord Postgres [db-ref conn]
  component/Lifecycle
  (start [self]
    (let [{:keys [subprotocol]} db-ref
          subprotocol (get (map-invert bonecp/default-subproto-map) (str subprotocol) (str subprotocol))
          db-ref' (assoc db-ref
                         :classname (bonecp/default-classname-map (str subprotocol))
                         :username (:user db-ref))
          ds (bonecp/make-datasource db-ref')]
      (.setLogStatementsEnabled ds true)
      (assoc self :conn {:datasource ds})))
  (stop [self]
    (when-let [{:keys [datasource]} conn]
      (.close datasource))
    (assoc self :conn nil)))

(defn postgresql [locator]
  (map->Postgres {:db-ref locator}))
