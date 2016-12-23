(ns time-tracker.time-sheet-events-test
  (:require-macros [cljs.core.async.macros :refer (go go-loop alt!)])
  (:require [cljs.test :refer-macros [deftest is testing async] :as t]
            [cljs.core.async :as casync :refer (>! <! timeout)]
            [clojure.test.check :as sc]
            [clojure.test.check.generators :as gen :refer [such-that]]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as pt :include-macros true]
            [time-tracker.time-sheet-events :as ev]))

(enable-console-print!)

(def +project-id+ 42)
(def +activity-id+ 43)

(def +timeout-ms+ 50)

(deftest ^:async events-process-terminates-on-port-closed
  (let [state-root (atom {})
        timebar-events (casync/chan (casync/buffer 10000))
        commands (casync/chan)
        handler (ev/process-ui-events! timebar-events commands state-root)]
    (async done
           (go
             (casync/close! timebar-events)

             (alt!
               (casync/into [] commands)
               ([cs] (is (= [] cs)))
               (timeout +timeout-ms+)
               ([x]
                (is false (str "Output channel not closed in " +timeout-ms+ "ms"))))

             (is (alt! handler ([_] :ok) (timeout +timeout-ms+) ([x] false))
                 (str "Output channel not closed in " +timeout-ms+ "ms"))
             (done)))))

(defn select-time-range! [timebar-events from to]
  (go
    (>! timebar-events {:type :time-selected :id :time-tracker/new-task :from from :to to})))

(deftest ^:async new-task-command-only-emitted-when-we-have-new-task
  (let [state-root (atom {})
        timebar-events (casync/chan (casync/buffer 10000))
        commands (casync/chan)
        handler (ev/process-ui-events! timebar-events commands state-root)]
    (async done
           (go
             (<! (select-time-range! timebar-events 0 1))

             (>! timebar-events {:type :field-changed :attribute :project_id :value +project-id+})
             (<! (select-time-range! timebar-events 0 1))

             (>! timebar-events {:type :field-changed :attribute :activity_id :value +activity-id+})
             (<! (select-time-range! timebar-events 0 1))

             (casync/close! timebar-events)
             (let [sent-commands (<! (casync/into [] commands))]
               (is (= [{:command :new-task! :from 0 :to 1 :info {:project_id +project-id+ :activity_id +activity-id+}}]
                      sent-commands)))
             (is (alt! handler ([_] :ok) (timeout +timeout-ms+) ([x] false))
                 (str "Output channel not closed in " +timeout-ms+ "ms"))

             (done)))))

(deftest ^:async should-notice-when-project-is-deselected
  (let [state-root (atom {})
        timebar-events (casync/chan (casync/buffer 10000))
        commands (casync/chan)
        handler (ev/process-ui-events! timebar-events commands state-root)]
    (async done
           (go
             (>! timebar-events {:type :field-changed :attribute :project_id :value +project-id+})
             (>! timebar-events {:type :field-changed :attribute :activity_id :value +activity-id+})
             (>! timebar-events {:type :field-changed :attribute :project_id :value nil})
             (<! (select-time-range! timebar-events 0 1))
             (casync/close! timebar-events)
             (let [sent-commands (<! (casync/into [] commands))]
               (is (empty? sent-commands)))
             (is (alt! handler ([_] :ok) (timeout +timeout-ms+) ([x] false))
                 (str "Output channel not closed in " +timeout-ms+ "ms"))

             (done)))))

(deftest ^:async should-notice-when-activity-is-deselected
  (let [state-root (atom {})
        timebar-events (casync/chan (casync/buffer 10000))
        commands (casync/chan)
        handler (ev/process-ui-events! timebar-events commands state-root)]
    (async done
           (go
             (>! timebar-events {:type :field-changed :attribute :project_id :value +project-id+})
             (>! timebar-events {:type :field-changed :attribute :activity_id :value +activity-id+})
             (>! timebar-events {:type :field-changed :attribute :activity_id :value nil})
             (<! (select-time-range! timebar-events 0 1))
             (casync/close! timebar-events)
             (let [sent-commands (<! (casync/into [] commands))]
               (is (empty? sent-commands)))
             (is (alt! handler ([_] :ok) (timeout +timeout-ms+) ([x] false))
                 (str "Output channel not closed in " +timeout-ms+ "ms"))

             (done)))))
