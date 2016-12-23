(ns time-tracker.date-time-test
  (:require [cljs.test :refer-macros [deftest is testing] :as t]
            [time-tracker.date-time :as dt]
            [time-tracker.time-zones :as tz]
            [cljs-time.core :as time])
  (:import [goog.date Date UtcDateTime]))

(enable-console-print!)

(deftest test-parsing-dates
  (let [parsed-date (dt/parse-date "2014-08-06")]
    (is (= true (instance? Date parsed-date)))
    (is (= (.getYear parsed-date) 2014))
    (is (= (.getMonth parsed-date) 7))
    (is (= (.getDate parsed-date) 6))))

(deftest test-parsing-date-time
  (let [parsed-date (dt/parse-timestamp "2014-08-06T12:34:56Z")]
    (is (= true (instance? Date parsed-date)))
    (is (= (.getUTCFullYear parsed-date) 2014))
    (is (= (.getUTCMonth parsed-date) 7))
    (is (= (.getUTCDate parsed-date) 6))
    (is (= (.getUTCHours parsed-date) 12))
    (is (= (.getUTCMinutes parsed-date) 34))
    (is (= (.getUTCSeconds parsed-date) 56))))

(deftest test-date-arithmetic
  (let [start-date (dt/parse-date "2014-08-06")]
    (is (= (dt/day-add start-date 10) (dt/parse-date "2014-08-16")))
    (is (= (dt/day-after start-date) (dt/parse-date "2014-08-07")))
    (is (= (dt/day-before start-date) (dt/parse-date "2014-08-05")))))

;; These test derived by using joda time to calculate equivalent dates from the repl.

(deftest london-zone
  (let [tz (tz/time-zone-for-id "Europe/London")]
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 29))    (time/date-time 2014  3 29  0 0)))
    "Transitions 2014-03-30T01:00+0000"
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 30))    (time/date-time 2014  3 30  0 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 30 1))   (time/date-time 2014  3 30  1 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 30 2))   (time/date-time 2014  3 30  1 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 30 3))   (time/date-time 2014  3 30  2 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 31))    (time/date-time 2014  3 30 23 0)))

    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10  1))    (time/date-time 2014  9 30 23 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10 25))    (time/date-time 2014 10 24 23 0)))
    "Transitions 2014-10-26T02:00+0100"
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10 26     0))   (time/date-time 2014 10 25 23 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10 26  0 59))  (time/date-time 2014 10 25 23 59)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10 26  1  0))   (time/date-time 2014 10 26  1 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10 27     0))   (time/date-time 2014 10 27  0 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10 27  1  0))   (time/date-time 2014 10 27  1 0)))))

(deftest greenland-zone
  (let [tz (tz/time-zone-for-id "America/Godthab")]
    "Transitions 2014-03-29T22:00-0300"
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 28))  (time/date-time 2014  3 28  3 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 29))  (time/date-time 2014  3 29  3 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 29 22 0))   (time/date-time 2014  3 30  1 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 29 22 59))  (time/date-time 2014  3 30  1 59)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 29 23 0))   (time/date-time 2014  3 30  1 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014  3 30  0 0))   (time/date-time 2014  3 30  2 0)))
    "Transitions 2014-10-25T23:00-0200"
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10 24))  (time/date-time 2014 10 24  2 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10 25))  (time/date-time 2014 10 25  2 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10 25 22 59))  (time/date-time 2014 10 26  0 59)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10 25 23 0))   (time/date-time 2014 10 26  2 0)))
    (is  (=  (dt/from-time-zone-as-utc tz (time/date-time 2014 10 26))  (time/date-time 2014 10 26  3 0)))))

(deftest auckland-zone
  (let [tz (tz/time-zone-for-id "Pacific/Auckland")]
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  4  5)) (time/date-time 2014  4  4 11  0)))
    "Transitions on 2014-04-06T3:00+1300"
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  4  6)) (time/date-time 2014  4  5 11  0)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  4  6  1 59)) (time/date-time 2014  4  5 12 59)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  4  6  2 00)) (time/date-time 2014  4  5 14  0)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  4  7)) (time/date-time 2014  4  6 12  0)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  9 27)) (time/date-time 2014  9 26 12  0)))
    "Transitions on 2014-09-28T2:00+1200"
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  9 28)) (time/date-time 2014  9 27 12  0)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  9 28  2  0)) (time/date-time 2014  9 27 14  0)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  9 28  2 59)) (time/date-time 2014  9 27 14 59)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  9 28  3  0)) (time/date-time 2014  9 27 14  0)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  9 28  3 59)) (time/date-time 2014  9 27 14 59)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  9 29)) (time/date-time 2014  9 28 11  0)))))

(deftest adak-zone
  (let [tz (tz/time-zone-for-id "America/Adak")]
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  3  9  1 30)) (time/date-time 2014  3  9 11 30)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  3  9  2 30)) (time/date-time 2014  3  9 12 30)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  3  9  3 30)) (time/date-time 2014  3  9 12 30)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014  3  9  4 00)) (time/date-time 2014  3  9 13  0)))

    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014 11  2  1 00)) (time/date-time 2014 11  2 10  0)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014 11  2  1 30)) (time/date-time 2014 11  2 10 30)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014 11  2  2  0)) (time/date-time 2014 11  2 12  0)))
    (is (= (dt/from-time-zone-as-utc tz (time/date-time 2014 11  2  2 30)) (time/date-time 2014 11  2 12 30)))))
