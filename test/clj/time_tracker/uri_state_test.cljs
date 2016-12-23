(ns time-tracker.uri-state-test
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [cljs.test :refer-macros [deftest is testing] :as t]
            [clojure.test.check :as sc]
            [clojure.test.check.generators :as gen :refer [such-that]]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as pt :include-macros true]
            [cljs.core.async :as async]
            [time-tracker.uri-state :as uri]
            [time-tracker.date-time :as dt])
  (:import goog.History))

(enable-console-print!)

(defn next-val-or-timeout [time-limit chan]
  (go
    (alt!
      chan ([route] route)
      (async/timeout time-limit) ::nothing-received)))

(defn set-fragment! [fragment]
  (.setToken (History.) fragment))

(defn get-fragment []
  (.getToken (History.)))

(deftest ^:async test-history-dispatcher
  (testing "History dispatch"
    (go
      (set-fragment! "")
      (let [route-events (async/chan)
            history (uri/run-dispatcher! route-events)]
        (is (= (<! (next-val-or-timeout 1 route-events)) {:type :show-root}))
        (set-fragment! "users/Freddy/2014-07-30")
        (is (= (<! (next-val-or-timeout 1 route-events))
               {:type :show-time-sheet :user-name "Freddy" :date (dt/parse-date "2014-07-30")}))
        (set-fragment! "users/Bob")
        (is (= (<! (next-val-or-timeout 1 route-events))
               {:type :show-time-sheet :user-name "Bob" :date (dt/today)}))
        (set-fragment! "")
        (is (= (<! (next-val-or-timeout 1 route-events))
               {:type :show-root}))
        (set-fragment! "preferences")
        (is (= (<! (next-val-or-timeout 1 route-events))
               {:type :show-preferences}))
        (uri/stop-dispatcher! history)))))
