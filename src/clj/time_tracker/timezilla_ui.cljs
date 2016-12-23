(ns time-tracker.timezilla-ui
  (:require [reagent.core :as reagent]
            [time-tracker.page-layout :refer [layout]]
            [time-tracker.server :as server :refer [fetch-users fetch-projects fetch-activities]]
            [time-tracker.uri-state :as uri]
            [ajax.core :as ajax]
            [cemerick.url :refer [url-encode]]
            [cljs.core.async :as async
             :refer [<! >! chan close! sliding-buffer put! alts!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

;; Convenience for Selenium etc. to check to see if there are any requests in
;; flight.
;; TODO this isn't great in practice though. If you specify a request that
;; takes a long time before sending off another, the first is not cancelled and
;; the last response will win.
;; All requests up to that point should be cancelled or have their return value
;; ignored.
(defonce num-requests-in-flight (atom 0))
(defonce current-result-in-flight (atom -1))

(def initial-timezilla-state
  {:form {:project []
          :user []
          :activity []
          :date-from "WEEK"
          :date-to "ALL"
          :report-query "BY_TIME"
          :hours-per-day "7"
          :time-granularity-mins "15"
          :rounding-type "PROPER"
          :report-heading []
          :limit 100}})

(defonce timezilla-state (reagent/atom initial-timezilla-state))

(extend-type js/HTMLCollection
  ISeqable
  (-seq [array] (array-seq array 0)))

(defn ^:export waiting-on-requests? [] (not= @num-requests-in-flight 0))

(defn get-query-results [config query]
  (let [url (server/relative-to config "/timezilla-result")
        resp-chan (async/chan)
        query (into {} (filter val query))]
    (prn ::get-query-results url query)
    (ajax/GET url {:response-format :json :keywords? true
                   :params query :headers {"Accept" "application/json"}
                   :handler (partial put! resp-chan)
                   :error-handler #(do (close! resp-chan) (prn %) (throw (ex-info "HTTP error" %)))})
    resp-chan))

(defn results-from-query! [state]
  (go
    (swap! num-requests-in-flight inc)
    (let [query-id (str (random-uuid))]
      (reset! current-result-in-flight query-id)
      (let [results (<! (get-query-results (:config @state) (assoc (:form @state) :query-id query-id)))]
        (swap! num-requests-in-flight dec)
        (if (= @current-result-in-flight (:query-id results))
          (swap! state assoc :results results)
          (prn query-id "does not equal" (:query-id results) "and so there's been out-of-order results, skipping"))))))

(defn event-value [ev]
  (let [target (. ev -target)
        element-type (. target -type)]
    (if (= element-type "select-multiple")
      (map
       (fn [opt] (. opt -value))
       (. target -selectedOptions))
      (. target -value))))

(defn update-form-and-results! [state k v]
  (swap! state assoc-in [:form k] v)
  (when-not (= k :report-heading)
    (results-from-query! state)))

(defn ^:export set-today!
  "Used to override when 'today' is, this is a convenience for testing."
  [yyyy-mm-dd]
  (update-form-and-results! timezilla-state :today yyyy-mm-dd))

(defn handle-change! [state ev]
  (let [target (.-target ev)
        localName (.-localName target)
        type (.-type target)
        ; input radio doesn't have a value because of
        ; uncontrolled input warnings. See https://github.com/lshift/timetracker-web/issues/72
        value-s (if (and (= localName "input") (= type "radio")) (.-id target) (.-value target))
        multiple (.-multiple target)
        value (if multiple
                (map #(.-value %) (.-selectedOptions target))
                (when-not (empty? value-s) value-s))
        field (keyword (.. ev -target -name))]
    (prn ::change! field value (.-multiple target))
    (update-form-and-results! state field value)
    (prn ::form! (-> state deref :form))

    (.preventDefault ev)))

(defn build-select-multiple [form key values changed!]
  [:select {:name (name key)
            :class (str (name key) "-select")
            :multiple true
            :size "10"
            :on-change changed!
            :value (key form)}
   (for [value (sort-by #(:name %) values)]
     ^{:key (:id value)} [:option
                          {:value (:id value)}
                          (:name value)])])

(defn build-select-single [form key values changed!]
  [:select {:name (name key)
            :class (str (name key) "-select")
            :on-change changed!
            :value (key form)}
   (for [value (keys values)]
     ^{:key value} [:option
                    {:value value}
                    (get values value)])])

(defn build-radio [form name values changed!]
  [:fieldset
   (for [value (keys values)
         :let [input [:input {:key value
                              :id value
                              :type "radio"
                              :name name
                              :checked (= (get form name) value)
                              :on-change changed!}]
               rest (get values value)
               div-attrs {:key (str "div-" value)}]]
     (if (string? rest)
       [:div div-attrs input rest]
       (into [:div div-attrs input] rest)))])

(def report-headers
  [{:id :user :name "User"}
   {:id :project :name "Project"}
   {:id :activity :name "Activity"}
   {:id :ticket :name "Ticket"}
   {:id :description :name "Task Description"}
   {:id :start_time :name "Start Time"}
   {:id :end_time :name "End Time"}
   {:id :days :name "Duration (work days)"}
   {:id :hours :name "Duration (hours)"}])

(defn report-headers-for-query-type []
  (let [query-type (-> @timezilla-state :form :report-query)
        skip-types (condp = query-type
                     "BY_TASK" #{:start_time :end_time}
                     "BY_USER" #{:start_time :end_time :ticket :description}
                     "BY_PROJECT" #{:user :activity :start_time :end_time :ticket :description}
                     #{})]
    (filter #(not (contains? skip-types (:id %))) report-headers)))

(defn coll-contains? [coll value]
  (some #(= value %) coll))

(defn csv-encode [data]
  (let [replace-commas (for [row data] (for [item row] (str "\"" item "\"")))
        join-rows (map #(clojure.string/join "," %) replace-commas)
        join-all (clojure.string/join "\n" join-rows)]
    join-all))

(defn build-csv [results form]
  (let [form-keys (map keyword (:report-heading form))
        selected (filter #(coll-contains? form-keys (:id %)) (report-headers-for-query-type))
        selected (if (empty? selected) (report-headers-for-query-type) selected)
        selected-keys (replace {:ticket :bug} (map :id selected))
        head (map :name selected)
        body (map #(for [k selected-keys] (get % k)) (:results results))
        csv (csv-encode (cons head body))]
    csv))

(defn download-as-csv [config form]
  (go
    (let [form* (assoc form :limit -1)
          res (<! (get-query-results config form*))
          csv (build-csv res form*)
          data-url (str "data:text/csv;charset=utf-8," (url-encode csv))
          el  (js/document.createElement "a")]
      (set! (.-download el) "timezilla.csv")
      (set! (.-href el) data-url)
      (js/document.body.appendChild el)
      (.click el)
      (js/document.body.removeChild el))))

(defn reporting-query-view [state changed!]
  (let [{:keys [config form users projects activities results]} @state]
    [:div {:class "timezilla-query-page"}
     [:form {:name "searchForm", :method "GET", :action "#"}
      [:div
       [:div {:class "tz-box"}
        [:h3 "Projects"]
        [build-select-multiple form :project projects changed!]]
       [:div {:class "tz-box"}
        [:h3 "Users"]
        [build-select-multiple form :user users changed!]]
       [:div {:class "tz-box"}
        [:h3 "Activities"]
        [build-select-multiple form :activity activities changed!]]
       [:div {:class "tz-box"}
        [:h3 "Date From"]
        [build-radio form
         :date-from {"WEEK" "Start of this week"
                     "LAST_WEEK" "Start of last week"
                     "MONTH" "Start of this month"
                     "LAST_MONTH" "Start of last month"
                     "YEAR" "Start of this year"
                     "ALL" "No start date"
                     "USER_SPECIFIED"
                     ["Specify "
                      ^{:key "div-specific-date-from"}
                      [:div {:class "tz-horizontal"}
                       ^{:key "input-specific-date-from"}
                       [:input {:type "text", :name "specific-date-from", :size "12" :on-change changed!}]
                       ^{:key "div-specific-date-from-format"}
                       [:div "(YYYY-MM-DD)"]]]}
         changed!]]
       [:div {:class "tz-box"}
        [:h3 "Date To"]
        [build-radio form
         :date-to {"ALL" "No end date"
                   "NOW" "Now"
                   "LAST_WEEK" "End of last week"
                   "LAST_MONTH" "End of last month"
                   "USER_SPECIFIED" ["Specify "
                                     [:div {:class "tz-horizontal"}
                                      [:input {:type "text", :name "specific-date-to", :size "12" :value (:specific-date-to form) :on-change changed!}]
                                      [:div "(YYYY-MM-DD)"]]]}
         changed!]]
       [:div {:class "tz-box"}
        [:h3 "Ticket IDs:"]
        [:input {:type "text", :name "bug-numbers", :size "8" :on-change changed!}]]]
      [:div
       [:div {:class "tz-box"}
        [:h3 "Report Headings"]
        [build-select-multiple form
         :report-heading (report-headers-for-query-type) changed!]]
       [:div {:class "tz-box"}
        [:h3 "Report Query"]
        [build-radio form
         :report-query {"BY_TIME" "By Time"
                        "BY_TASK" "By Task"
                        "BY_USER" "By User"
                        "BY_PROJECT" "By Project"}
         changed!]
        [:h3 "Limit"]
        [:input {:type "number" :id "limit" :name "limit" :on-change changed! :value (:limit form)}]
        [:a {:class "pure-button" :on-click #(update-form-and-results! state :limit -1)}
         "No Limit"]
        [:h3 "CSV"]
        [:a {:class "pure-button"
             :on-click #(download-as-csv config form)}
         "Download all as CSV"]]
       [:div {:class "tz-box"}
        [:h3 "Hours per Day"]
        [:input {:type "text", :name "hours-per-day", :size "3", :value (:hours-per-day form) :on-change changed!}]]
       [:div {:class "tz-box"}
        [:div
         [:h3 "Time Granularity"]
         [build-select-single form
          :time-granularity-mins
          {"0" "No granularity"
           "5" "5 mins"
           "10" "10 mins"
           "15" "15 mins"
           "30" "30 mins"
           "60" "1 hour"
           "120" "2 hours"}
          changed!]]
        [:div
         [:h3 "Rounding Type"]
         [:select {:name "rounding-type" :on-change changed!}
          [:option {:value "PROPER"} "Proper"]
          [:option {:value "UP"} "Up"]]]
        [:p {:class "note"} "Note: values between 0 and the granularity value are always rounded up"]]]]
     [:div
      [:h3 "Results"]
      [:div
       [:table {:class "results-total"}
        (let [two-dp #(.toFixed % 2)]
          [:tbody
           [:tr
            [:th "Total Days: "]
            [:td {:id "total-days"} (->> results :results (map :days) (reduce +) two-dp)]]
           [:tr
            [:th "Total Hours: "]
            [:td {:id "total-hours"} (->> results :results (map :hours) (reduce +) two-dp)]]])]]
      [:div {:class "results"}
       [:div
        (let [keys (map keyword (:report-heading form))
              keys (if (empty? keys) (map #(:id %) (report-headers-for-query-type)) keys)
              headers (apply merge (map #(hash-map (:id %) (:name %)) (report-headers-for-query-type)))]
          [:table {:class "results" :style {:width "100%"}}
           [:thead [:tr
                    (for [key keys]
                      ^{:key key}
                      [:th (get headers key)])]]
           [:tbody
            (for [row (:results results)]
              ^{:key (str "results-" (:id row))}
              [:tr {:class "results-row"}
               (for [key keys]
                 ^{:key (str "td-" (:id row) "-" key)}
                 [:td {:class (str "results-" (name key))}
                  (if (= key :ticket)
                    ;; Try to link to the ticket, but if that's not possible just
                    ;; show the bug ID
                    (let [{:keys [title link]} (:ticket row)]
                      [:a
                       (if (seq link) {:href (get-in row [key :link])} {})
                       (if (seq title) title (:bug row))])
                    (get row key))])])]])]]]]))

(defn timezilla-query [config user]
  (swap! timezilla-state assoc :config config)
  (go
    (let [users (vals (<! (fetch-users config)))
          projects (vals (<! (fetch-projects config)))
          activities (vals (<! (fetch-activities config)))]
      (swap!
       timezilla-state
       #(-> %
            (assoc :users users)
            (assoc :projects projects)
            (assoc :activities activities)
            (assoc :loaded? true)))
      (results-from-query! timezilla-state)))
  (fn []
    (let [{:keys [loaded?]} @timezilla-state]
      [layout {:current-user user}
       (if loaded?
         [reporting-query-view timezilla-state
          (partial handle-change! timezilla-state)]
         [:span "Loading..."])])))
