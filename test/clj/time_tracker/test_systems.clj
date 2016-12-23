(ns time-tracker.test-systems
  (:require [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as j]
            [ring.mock.request :refer [request body]]
            [time-tracker.db-env :refer [db-from-env]]
            [time-tracker.environ :refer [core-system]]
            [time-tracker.postgresql :as postgresql]
            [time-tracker.ring :as ring]))

(defn clean-db [system]
  (let [conn (-> system :db :conn)]
    (j/execute! conn ["TRUNCATE task_time, tasks"])))

(defn empty-table! [system table]
  (let [conn (-> system :db :conn)]
    (j/delete! conn table [])))

(defn ok? [response]
  (= (:status response) 200))

(defn no-content? [response]
  (and (= (:status response) 204)
       (= (:body response) "")))

(defn created? [response]
  (= (:status response) 201))

(defn random-string []
  (str (java.util.UUID/randomUUID)))

(defn get-location-header [response]
  (-> response :headers (get "Location")))

(defn json? [response]
  (re-find #"application/json" (-> response :headers (get "Content-Type"))))

(defn json-body [response]
  (-> response :body (json/read-str :key-fn keyword)))

(defmacro with-system
  "*Unhygienic* macro which will initialise and start a new system (minus
  web-server). Will lexically bind system and handler. Stops the system
  after the body completes."
  [& body]
  ;; N.B. system# and ~'system pair is to prevent someone clobbering
  ;; system inside the body and preventing stopping of the component
  `(let [system#   (component/start (core-system (db-from-env)))
         ~'system  system#
         ~'handler (-> ~'system :web-app ring/request-handler)]
     (try
       (clean-db system#)
       ~@body
       (finally
         (component/stop system#)))))
