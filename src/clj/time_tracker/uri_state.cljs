(ns time-tracker.uri-state
  (:require [secretary.core :as secretary :include-macros true :refer-macros [defroute]]
            [goog.events :as gevents]
            [cljs.core.async :as async
             :refer [<! >! chan close! sliding-buffer put! alts!]]
            [time-tracker.date-time :as dt])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:import goog.History
           goog.history.EventType))

(secretary/set-config! :prefix "#")

(defroute root-path "/" []
  {:type :show-root})

(defroute time-sheet-dated-path "/users/:user/:date" {:keys [user date] :as params}
  {:type :show-time-sheet
   :user-name user
   :date (dt/parse-date date)})

(defroute time-sheet-path "/users/:user" {:keys [user] :as params}
  {:type :show-time-sheet
   :user-name user
   :date (dt/today)})

(defroute preferences "/preferences" []
  {:type :show-preferences})

(defroute timezilla "/timezilla" []
  {:type :show-timezilla-query})

(defroute admin "/admin" []
  {:type :show-admin})

(defn parse-route [fragment]
  (secretary/dispatch! fragment))

;; TODO: Redirect to root)
(defroute "*" {:as params}
  (prn ::unknown-route params)
  {:type :unknown-route})

(defn dispatch! [events-ch fragment]
  (let [route (parse-route fragment)]
    (if route
      (put! events-ch route)
      (prn ::un-handled-route route))))

(defn update-fragment! [fragment]
  (prn ::update-fragment fragment)
  (set! js/document.location.hash fragment))

(defn run-dispatcher! [events-ch]
  (let [h (History.)
        dispatcher (partial dispatch! events-ch)
        listener-fn #(when (.-isNavigation %) (dispatcher (.-token %)))
        listener-key (gevents/listen h EventType.NAVIGATE listener-fn)]
    (.setEnabled h true)

    (dispatcher (.getToken h))

    {:unlisten #(gevents/unlisten dispatcher EventType.NAVIGATE listener-fn)}))

(defn stop-dispatcher! [{:keys [unlisten]}]
  (unlisten))
