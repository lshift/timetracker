(ns time-tracker.graphs-data
  (:require  [com.stuartsierra.component :as component]
             [liberator.core :refer [resource defresource]]
             [compojure.core :refer [routes ANY GET]]
             [compojure.route :as route]
             [yesql.core :refer [defquery]]
             [clojure.java.jdbc :as j]
             [clojure.set :refer [difference]]
             [time-tracker.ring :as ring]
             [ring.middleware.params :refer [wrap-params]]
             [ring.middleware.keyword-params :refer [wrap-keyword-params]]
             [clojure.data.json :as json]
             [clojure.pprint :refer [pprint]]
             [clj-time.core :as t]
             [clj-time.format :as f]
             [clj-time.coerce :as tc]
             [schema.core :as s]
             [schema.coerce :as sc]
             [schema.utils :as su]
             [clojure.tools.logging :as log]
             [time-tracker.db-types])
  (:import [org.joda.time LocalDate]))

(defquery query-projects-by-day "sql/query-projects-by-day.sql")
(defquery query-projects-for-period "sql/query-projects-for-period.sql")
(defquery query-total-hours-for-period "sql/query-total-hours-for-period.sql")
(defquery query-hours-by-day "sql/query-hours-by-day.sql")

(extend-protocol json/JSONWriter
  org.joda.time.LocalDate
  (-write [self writer]
    (json/write (f/unparse-local-date (f/formatters :date) self) writer)))

(def coercions
  (merge sc/+json-coercions+
         {LocalDate (sc/safe (partial f/parse-local-date (f/formatters :basic-date)))}))

(def user-period-query
  {:start-date LocalDate :end-date LocalDate :user-name s/Str})

(def parse-user-period-query
  (sc/coercer user-period-query coercions))

(def user-date-query
  {:date LocalDate :user-name s/Str})

(def parse-user-date-query
  (sc/coercer user-date-query coercions))

(defn parse-params-with [parser]
  (fn [ctx]
    (let [query (parser (get-in ctx [:request :params]))]
      (prn ::query query)
      (if (not (su/error? query))
        [false {::query query}]
        [true {::validation-failure query}]))))

(defn- handle-malformed [{:keys [validation-failure]}]
  (log/error ::handle-malformed validation-failure)
  (pr-str {:validation-failure validation-failure}))

(defn- date-range [start end]
  (->> start
       (iterate #(t/plus % (t/days 1)))
       (take-while #(t/before? % end))))

(defn projects-by-day [{:keys [conn] :as db}]
  (resource
   :available-media-types ["application/json"]
   :allowed-methods [:get]
   :malformed? (parse-params-with parse-user-period-query)
   :exists?
   (fn [{:keys [::query]}]
     (prn ::query query)
     (let [{:keys [user-name start-date end-date]} query
           data (->> (query-projects-by-day {:user_name user-name :start_date start-date :end_date end-date} {:connection conn})
                     (map #(update-in % [:day] (partial f/parse-local-date (f/formatters :date)))))]
       {::data data}))

   :handle-ok
   (fn [{:keys [::data ::query] :as ctx}]
     (let [{:keys [start-date end-date]} query
           dates (date-range start-date end-date)
           grouped (group-by :day data)]
       (reduce
        (fn [grouped date]
          (if (contains? grouped date)
            grouped
            (assoc grouped date [])))
        grouped dates)))

   :handle-malformed handle-malformed

   :handle-exception (fn [{:keys [exception]}] (log/error exception "in projects by day"))))

(defn start-of-week [d]
  (let [dow (t/day-of-week d)
        adjustment (-> dow dec - t/days)]
    (t/plus d adjustment)))

(defn projects-for-week [{:keys [conn] :as db}]
  (resource
   :available-media-types ["application/json"]
   :allowed-methods [:get]
   :malformed? (parse-params-with parse-user-date-query)

   :exists?
   (fn [{:keys [::query]}]
     (let [{:keys [user-name date]} query
           start (start-of-week date)
           end (t/plus start (t/days 7))
           prev-start (t/plus start (t/days -7))
           by-project (query-projects-for-period {:user_name user-name :start_date start :end_date end} {:connection conn})
           prev-week-total (first (query-total-hours-for-period {:user_name user-name :start_date prev-start :end_date start} {:connection conn}))]

       (prn ::by-project by-project)
       {::by-project by-project ::prev-week prev-week-total}))

   :handle-ok
   (fn [{:keys [::by-project ::period ::prev-week]}]
     {:current-week
      (zipmap (map :id by-project)
              (map #(select-keys % [:name :hours]) by-project))
      :prev-week prev-week})

   :handle-exception (fn [{:keys [exception]}] (log/error exception "in projects by day"))))

(defn hours-by-day [{:keys [conn] :as db}]
  (resource
   :available-media-types ["application/json"]
   :allowed-methods [:get]
   :malformed? (parse-params-with parse-user-period-query)

   :exists?
   (fn [{:keys [::data ::query] :as ctx}]
     (let [{:keys [user-name start-date end-date]} query
           data (->> (query-hours-by-day {:user_name user-name :start_date start-date :end_date end-date} {:connection conn})
                     (map #(update-in % [:day] (partial f/parse-local-date (f/formatters :date)))))]
       {::data data}))

   :handle-ok
   (fn [{:keys [::data ::query] :as ctx}]
     (let [{:keys [start-date end-date]} query
           dates (into #{} (date-range start-date end-date))
           known-dates (into #{} (map :day data))
           missing-dates (difference dates known-dates)
           extras (map #(hash-map :day % :hours 0) missing-dates)
           complete (concat data extras)
           fmt (f/formatter "e")]
       (zipmap (map :day complete)
               (map #(let [{:keys [hours day]} %] [{:hours hours :project (f/unparse-local fmt day)}]) complete))))

   :handle-malformed handle-malformed

   :handle-exception (fn [{:keys [exception]}] (log/error exception "in projects by day"))))

(defn app [{:keys [db] :as self}]
  (routes
   (ANY "/graphs/projects-by-day" []
     (projects-by-day db))
   (ANY "/graphs/projects-for-week" []
     (projects-for-week db))
   (ANY "/graphs/hours-by-day" []
     (hours-by-day db))))

(defrecord GraphsService [db]
  ring/RingRequestHandler
  (request-handler [self]
    (-> self app wrap-keyword-params wrap-params)))

(defn instance []
  (map->GraphsService {}))
