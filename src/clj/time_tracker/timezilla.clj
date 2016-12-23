(ns time-tracker.timezilla
  (:require
   [liberator.core :refer [resource]]
   [compojure.core :refer [routes ANY GET]]
   [time-tracker.ring :as ring]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.nested-params :refer [wrap-nested-params]]
   [clojure.tools.logging :as log]
   [clojure.data.json :as json]
   [time-tracker.timezilla-data :as td]))

(extend-type java.sql.Timestamp
  json/JSONWriter
  (-write [date out]
    (json/-write (str date) out)))

(defn timezilla-results [db queries]
  (resource
   :available-media-types ["application/json"]
   :allowed-methods [:get]
   :exists?
   (fn [{:keys [request]}]
     (log/info ::params (pr-str (:params request)))
     (let [res (td/run-query db (:params request))]
       {::results res}))
   :handle-ok
   (fn [{:keys [::results] :as ctx}]
     (log/info ::results results)
     results)
   :handle-exception
   (fn [{:keys [exception]}] (log/error exception "in timezilla-results"))))

(defn app [{:keys [db queries] :as self}]
  (routes
   (ANY "/timezilla-result" []
     (timezilla-results db queries))))

(defrecord TimezillaUI [queries]
  ring/RingRequestHandler
  (request-handler [self]
    (-> self app wrap-keyword-params wrap-nested-params wrap-params)))

(defn instance []
  (map->TimezillaUI {}))
