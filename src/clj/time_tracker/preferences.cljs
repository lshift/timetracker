(ns time-tracker.preferences
  (:require [cljs.core.async :as async
             :refer [<! >! chan close! sliding-buffer put! alts!]]
            [time-tracker.date-time :as dt]
            [time-tracker.time-zones :as tz]
            [time-tracker.ui-widgets :refer [uncontrolled-input select-dropdown]]
            [time-tracker.page-layout :refer [layout]]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]
            [cljs.reader :as reader]
            [reagent.core :as reagent :refer [atom]]
            [kioo.reagent :refer [content set-attr do-> substitute listen]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]
                   [kioo.reagent :refer [defsnippet deftemplate]])
  (:import [goog.date Interval]))

(def default-prefs {:start-time 7 :end-time 21 :timezone "Europe/London"})

(defn start-end-preference-changed! [out-events what value]
  (put! out-events {:type :set-preference! :attribute what :value value}))

(defsnippet render-prefs "time_tracker/preferences.html" [:.preferences-form]
  [{:keys [start-time end-time timezone reset-on-new-task]
    :or {:start-time (:start-time default-prefs)
         :end-time   (:end-time default-prefs)
         :timezone   (:timezone default-prefs)
         :reset-on-new-task   (:reset-on-new-task default-prefs)}}
   out-events]
  {[:.start-time] (content (str start-time ":00"))
   [:.end-time] (content (str end-time ":00"))
   [:input#start-time] (uncontrolled-input start-time
                                           #(start-end-preference-changed! out-events
                                                                           :start-time (int (.. % -target -value))))
   [:input#end-time] (uncontrolled-input end-time
                                         #(start-end-preference-changed! out-events
                                                                         :end-time (int (.. % -target -value))))
   [:.timezone-selector]
   (substitute
    [select-dropdown {:name "timezone" :placeholder "TimeZone"
                      :on-change #(put! out-events
                                        {:type :set-preference!
                                         :attribute :timezone :value %})
                      :value timezone
                      :options (into (sorted-map)
                                     (map (juxt identity identity)
                                          (tz/time-zone-ids)))}])
   [:input#reset-on-new-task]
   (do->
    (set-attr :checked reset-on-new-task)
    (listen :on-change
            #(put! out-events {:type :set-preference!
                               :attribute :reset-on-new-task :value (.. % -target -checked)})))})

(def prefs-storage-key "timetracker-prefs")

(defn save-preferences! [prefs]
  (let [prefs-str (pr-str prefs)]
    (aset (.-localStorage js/window) prefs-storage-key prefs-str)))

(defn load-preferences []
  (let [prefs-data (aget (.-localStorage js/window) prefs-storage-key)]
    (when prefs-data
      (reader/read-string prefs-data))))

(defn update-prefs [prefs attribute value]
  (let [{:keys [start-time end-time] :as prefs'} (assoc prefs attribute value)]
    (if (>= start-time end-time)
      (cond
        (= start-time 24) (assoc prefs' :start-time 23 :end-time 24)
        (= end-time 0) (assoc prefs' :start-time 0 :end-time 1)
        (= attribute :start-time) (assoc prefs' :end-time (inc start-time))
        (= attribute :end-time) (assoc prefs' :start-time (dec end-time))
        :else prefs')
      prefs')))

(defn update-timebar-meta [bounds {:keys [start-time end-time timezone reset-on-new-task]} current-date]
  (let [tz (tz/time-zone-for-id (or timezone "Europe/London"))
        start-time-l (t/date-time
                      (t/year current-date) (t/month current-date) (t/day current-date)
                      start-time 0)
        end-time-l (t/date-time
                    (t/year current-date) (t/month current-date) (t/day current-date)
                    end-time 0)
        start-inst (dt/from-time-zone-as-utc tz start-time-l)
        end-inst (dt/from-time-zone-as-utc tz end-time-l)]
    (assoc bounds :start-time start-inst :end-time end-inst :tz tz :reset-on-new-task reset-on-new-task)))

(defn process-prefs-events! [prefs-changes state-root]
  (go-loop []
    (let [{:keys [prefs current-date]} @state-root]
      (when-let [{:keys [attribute value] :as e} (<! prefs-changes)]
        (let [new-prefs (update-prefs prefs attribute value)]
          (swap! state-root update-in [:prefs] merge new-prefs)
          ;; Persuade reagent to force refresh the form, and thus form values.
          ;; Helpfully, at some point javascript numbers are floats, and so at
          ;; some point (= % (inc %)) holds true. We use a large-ish, prime
          ;; from http://oeis.org/A014234/list somewhat arbitrarily, albeit
          ;; with the assumption that you can't cause that many events between
          ;; repaints.
          (swap! state-root
                 update-in [:prefs :serial-number] #(rem (inc %) 16777213))
          (save-preferences! (:prefs @state-root)))
        (recur)))))

(defn preferences [state]
  (if (contains? @state :prefs-changes)
    (async/close! (:prefs-changes @state)))
  (let [prefs-changes (chan)]
    (swap! state assoc :prefs-changes prefs-changes)
    (process-prefs-events! prefs-changes state)
    (fn [state]
      (let [{:keys [prefs current-user prefs-changes]} @state]
        (prn ::root-keys (keys @state) prefs)
        [layout {:header-menus nil :current-user current-user}
         [render-prefs prefs prefs-changes]]))))
