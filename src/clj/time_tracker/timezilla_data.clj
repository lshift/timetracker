(ns time-tracker.timezilla-data
  (:require
   [yesql.core :refer [defquery]]
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clj-time.coerce :as tc]
   [clj-http.client :as client]
   [clojure.string :as s]
   [clojure.tools.logging :as log]
   [clojure.data.json :as json]
   [time-tracker.db-types])
  (:import [org.joda.time LocalDate DateTimeConstants Days]))

(defquery query-hours-by-time "sql/query-hours-by-time.sql")
(defquery query-hours-by-task "sql/query-hours-by-task.sql")
(defquery query-hours-by-user "sql/query-hours-by-user.sql")
(defquery query-hours-by-project "sql/query-hours-by-project.sql")

(defn parse-specific [specific default]
  (try
    (f/parse (f/formatters :date) specific)
    (catch Exception e
      (do
        (log/warn (format "Bad date: %s" specific))
        default))))

(defn date-from [today query]
  (let [val (:date-from query)
        specific (:specific-date-from query)
        week (t/minus today (t/days (- (t/day-of-week today) 1)))]
    (case (keyword val)
      :WEEK week
      :LAST_WEEK (t/minus week (t/days 7))
      :MONTH (t/first-day-of-the-month today)
      :LAST_MONTH (t/first-day-of-the-month (t/minus today (t/months 1)))
      :YEAR (t/date-time (t/year today))
      :ALL (t/epoch)
      :USER_SPECIFIED (parse-specific specific (t/epoch))
      nil)))

(defn date-to [today query]
  (let [val (:date-to query)
        specific (:specific-date-to query)
        ;; N.B. that now is actually midnight tonight
        ;; TODO is this reasonable???
        now (t/plus today (t/days 1))
        max-time (t/date-time 9999)]
    (case (keyword val)
      :ALL max-time
      :NOW now
      :LAST_WEEK (t/minus today (t/days (- (t/day-of-week today) 1)))
      :LAST_MONTH (t/first-day-of-the-month today)
      :USER_SPECIFIED (parse-specific specific max-time)
      nil)))

(defn get-report-query [query]
  (let [val (:report-query query)]
    (case (keyword val)
      :BY_TIME query-hours-by-time
      :BY_TASK query-hours-by-task
      :BY_USER query-hours-by-user
      :BY_PROJECT query-hours-by-project
      nil)))

(defn granulator [minutes granularity kind]
  (cond
    (= 0 granularity) minutes
    (< minutes granularity) granularity
    :else (let [remainder (rem minutes granularity)
                lowGran (- minutes remainder)
                highGran (+ lowGran granularity)
                midPoint (- highGran (/ (- highGran lowGran) 2))]
            (case kind
              :PROPER (if (< minutes midPoint) lowGran highGran)
              :UP (if (= remainder 0) lowGran highGran)
              (throw (Exception. kind))))))

(defn ticket-details [tracker-url bug]
  (if (and tracker-url bug)
    (try
      (let [details (-> (format tracker-url bug) client/get :body json/read-str)]
        {:link (get details "html_url" "")
         :title (get details "title" "")
         :state (get details "state" "")})
      (catch Exception e {:link "" :title "" :state ""}))
    {:link "" :title "" :state ""}))

(defn parsed-minutes [row {:keys [minutes-per-day granularity kind]}]
  (->
   row
   (assoc :raw-minutes (:minutes row)
          :minutes (granulator (:minutes row) granularity kind)
          :ticket (ticket-details (:tracker_url row) (:bug row)))
   (#(assoc %
            :hours (/ (:minutes %) 60)
            :days (Double/parseDouble (format "%.2f" (float (/ (:minutes %) minutes-per-day))))))))

(defn run-query [db raw-query]
  (log/debug ::run-query db raw-query)
  (let [users (-> (:user raw-query) vals (#(map read-string %)))
        projects (-> (:project raw-query) vals (#(map read-string %)))
        activities (-> (:activity raw-query) vals (#(map read-string %)))
        granularity (-> (:time-granularity-mins raw-query) Integer/parseInt)
        hours-per-day (-> (:hours-per-day raw-query) Integer/parseInt)
        report-query (get-report-query raw-query)
        minutes-per-day (* 60 hours-per-day)
        rounding-type (-> (:rounding-type raw-query) keyword)
        bugid (when-let [bs (:bug-numbers raw-query)] (s/split bs #","))
        today (or (tc/from-string (:today raw-query)) (t/today))
        query-id (or (:query-id raw-query) -1)
        query {:projectid (when-not (empty? projects) projects)
               :has_projectid (-> projects empty? not)
               :userid (when-not (empty? users) users)
               :has_userid (-> users empty? not)
               :activityid (when-not (empty? activities) activities)
               :has_activityid (-> activities empty? not)
               :week_start (tc/to-sql-date (date-from today raw-query))
               :week_end (tc/to-sql-date (date-to today raw-query))
               :bugid bugid
               :has_bugid (not (empty? bugid))
               ;; We can't send 'LIMIT ALL' to the parameterised SQL query, so
               ;; we use MAX_VALUE instead when we want to not have a limit
               :limit  (let [lim (Integer/parseInt (:limit raw-query))]
                         (if (> lim 0)
                           lim
                           Integer/MAX_VALUE))}]
    (log/debug ::query query)
    (let [raw-results
          (report-query query {:connection (:conn db)})
          parsed-results
          (map #(parsed-minutes % {:minutes-per-day minutes-per-day
                                   :granularity granularity
                                   :kind rounding-type}) raw-results)]
      {:raw-query (prn-str raw-query)
       :query (prn-str query)
       :results parsed-results
       :query-id query-id})))
