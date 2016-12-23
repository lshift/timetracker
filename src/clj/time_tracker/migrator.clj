(ns time-tracker.migrator
  (:require  [com.stuartsierra.component :as component]
             [clojure.tools.logging :as log]
             [clojure.java.jdbc :as jdbc])
  (:import [org.flywaydb.core Flyway]))

(defn get-ds [{:keys [datasource name]}]
  (cond
    datasource datasource
    name
    (jdbc/when-available
     javax.naming.InitialContext
     (let [context (javax.naming.InitialContext.)]
       (.lookup context ^String name)))))

(defrecord Migrator [db]
  component/Lifecycle
  (start [self]
    (log/info ::migrating-with db)
    (let [ds (-> db :conn get-ds)
          migrator (Flyway.)]
      (assert ds "Db must provide datasource in [:conn :datasource] or JNDI name in [:conn :name]")
      (log/info ::ds ds)
      (doto migrator
        (.setDataSource ds)
        (.migrate)))
    self)

  (stop [self] self))

(defn instance []
  (map->Migrator {}))
