(ns time-tracker.bootstrap
  (:require [time-tracker.time-sheets :as ts]
            [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async
             :refer [<! >! chan close! sliding-buffer put! alts!]]
            [time-tracker.date-time :as dt]
            [time-tracker.preferences :as prefs :refer [preferences]]
            [time-tracker.users-list :refer [render-user-list]]
            [time-tracker.page-layout :refer [layout]]
            [time-tracker.time-sheets :refer [time-sheet]]
            [time-tracker.time-sheet-events :refer [+new-task+]]
            [time-tracker.sorry-dave :refer [sorry-dave]]
            [time-tracker.timezilla-ui :refer [timezilla-query]]
            [time-tracker.admin :refer [admin]]
            [time-tracker.event-handling :refer [process-messages]]
            [time-tracker.uri-state :as uri]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]
                   [cljs.core.match.macros :refer [match]]))

(enable-console-print!)

(defn init-state []
  (let [prefs' (prefs/load-preferences)
        prefs  (merge prefs/default-prefs prefs')
        date   (dt/today)
        bounds (prefs/update-timebar-meta {:height 24 :width 480}
                                          prefs date)]
    {:projects nil
     :activities nil :tasks nil
     :new-task +new-task+
     :editing? #{}
     :indicated-time nil
     :timebar-meta bounds
     :prefs prefs}))

(defonce state-root (atom (init-state)))
;; We add a watch so that update-timebar-meta is always called whenever
;; :current-date or :prefs changes
(add-watch
 state-root
 :update-timebar-meta
 (fn [_key ref old new]
   (if (and (:current-date new)
            (or (not= (:current-date old) (:current-date new))
                (not= (:prefs old) (:prefs new))))
     (swap! ref #(assoc % :timebar-meta (prefs/update-timebar-meta {:height 24 :width 480}
                                                                   (:prefs %)
                                                                   (:current-date %)))))))

(defn run-exit-logger [children]
  (go
    (loop [children children]
      (let [[v c] (alts! (vec (keys children)))]
        (prn ::child-exited! :child (children c) :value v)
        (recur (dissoc children c))))))

(defmulti -render-root (fn [{:keys [type]} props] type))

(defmethod -render-root :show-root [_ {:keys [state config]}]
  (let [{:keys [timebar-events current-user] :as root} @state]
    [layout {:header-menus nil :current-user current-user}
      ;; TODO: We should have a seperate config store thingy.
     [render-user-list {:config config :events-ch timebar-events}]]))

(defmethod -render-root :show-time-sheet [{:keys [user-name date] :as component-state} {:keys [state config]}]
  [time-sheet {:state state :user-name user-name :date date :config config}])

(defmethod -render-root :show-preferences [_ {:keys [state]}]
  [preferences state])

(defmethod -render-root :show-timezilla-query [_ {:keys [config state]}]
  (let [user (-> state deref :current-user)]
    [timezilla-query config user]))

(defmethod -render-root :show-admin [_ {:keys [config state]}]
  [admin config state])

(defmethod -render-root :default [_ _]
  [sorry-dave])

(defn -process-uri-change [state msg]
  (go
    (doto state
      (swap! assoc :route msg)
      (->> deref (prn ::route-now)))))

(defonce component-state (reagent/atom {:route {:type :show-root}}))

(defn setup-dispatcher []
  (let [uri-events (chan)]
    (swap! component-state assoc :dispatcher (uri/run-dispatcher! uri-events))
    (process-messages uri-events -process-uri-change {:init-state component-state})))

(defn root-component [props]
  [-render-root (:route @component-state) props])

(defonce dom-elt (js/document.getElementById "application"))

(defn boot-component [config]
  [root-component {:state state-root :config config}])

(defn mount-root []
  (let [app-base-url (.. js/document -location -pathname)
        time-sheets (boot-component {:base-url app-base-url})]
    (reagent/render time-sheets dom-elt)))

(defn ^:export start-app []
  (setup-dispatcher)
  (mount-root))

(defn ^:export fig-reload []
  (mount-root))

(defonce start-once (start-app))
