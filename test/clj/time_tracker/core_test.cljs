(ns time-tracker.core-test
  (:require
   [cljs.test :as test]
   [doo.runner :refer-macros [doo-tests]]
   [time-tracker.teamcity]
   [time-tracker.date-time-test]
   [time-tracker.intervals-test]
   [time-tracker.time-sheet-events-test]
   [time-tracker.uri-state-test]))

(doo-tests
 'time-tracker.date-time-test
 'time-tracker.intervals-test
 'time-tracker.time-sheet-events-test
 'time-tracker.uri-state-test)
