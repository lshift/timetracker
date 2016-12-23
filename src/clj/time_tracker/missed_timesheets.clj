(ns time-tracker.missed-timesheets
  (:require  [com.stuartsierra.component :as component]
             [liberator.core :refer [resource defresource]]
             [compojure.core :refer [routes ANY GET]]
             [compojure.route :as route]
             [net.cgrand.enlive-html :as html]
             [yesql.core :refer [defquery]]
             [time-tracker.ring :as ring]
             [clojure.data.json :as json]
             [clojure.pprint :refer [pprint]]
             [clj-time.core :as t]
             [clj-time.format :as f]
             [clojure.tools.logging :as log]))

(defquery query-missed-timesheets "sql/query-missed-timesheets-by-user.sql")

(html/defsnippet missed-row "time_tracker/missed-timesheets.html" [:#tardy-users :tbody :tr]
  [{:keys [last_entry hours days name]}]
  [:.last-entry] (html/content (str last_entry))
  [:.worked-days] (html/content (str days))
  [:.worked-hours] (html/content (str hours))
  [:.user-name] (html/content (str name)))

(html/deftemplate template "time_tracker/missed-timesheets.html"
  [rows]
  [:#tardy-users :tbody] (html/content (map missed-row rows)))

(defn forgotten-timesheets [{:keys [conn] :as db}]
  (resource
   :available-media-types ["text/html"]
   :allowed-methods [:get]
   :exists? (fn [ctx]
              (let [data (query-missed-timesheets {} {:connection conn})]
                {::data data}))
   :handle-ok (fn [{:keys [::data]}]
                (apply str (template data)))
   :etag (fn [{:keys [::data]}] (format "%08x" (hash data)))
   :handle-exception (fn [{:keys [exception]}] (log/error exception "in missing timesheet report"))))

(defn app [{:keys [db] :as self}]
  (ANY "/forgotten-timesheets" []
    (forgotten-timesheets db)))

(defrecord MissingTimesheetsService [db]
  ring/RingRequestHandler
  (request-handler [self]
    (-> self app)))

(defn instance []
  (map->MissingTimesheetsService {}))
