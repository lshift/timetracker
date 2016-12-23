(ns time-tracker.timezilla-data-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [clojure.data :refer [diff]]
   [clj-time.coerce :as tc]
   [time-tracker.timezilla-data :as td]))

(deftest granularity-testing
  (is (= 10 (td/granulator 0 10 :DONTCARE)))
  (is (= 10 (td/granulator 5 10 :DONTCARE)))
  (is (= 10 (td/granulator 10 10 :PROPER)))
  (is (= 20 (td/granulator 15 10 :PROPER)))
  (is (= 20 (td/granulator 11 10 :UP)))
  (is (= 360 (td/granulator 315 60 :UP))))

(defn diffeq [a b]
  (let [[only-in-a only-in-b both] (diff a b)]
    (if (and (nil? only-in-a) (nil? only-in-b))
      (is true)
      (is (= a b)
          (format "Only in first part: %s\nOnly in second part %s" only-in-a only-in-b)))))

(deftest minute-parser
  (is (= 0.06 (:days (td/parsed-minutes
                      {:minutes 10}
                      {:minutes-per-day (* 60 8)
                       :granularity 30
                       :kind :PROPER}))))
  (is (= 0.86 (:days (td/parsed-minutes
                      {:minutes 315}
                      {:minutes-per-day (* 60 7)
                       :granularity 60
                       :kind :UP})))))

(deftest date-from
  (let [today (tc/from-string "2016-07-10")]
    (is (= (tc/from-string "2016-07-04") (td/date-from today {:date-from "WEEK"})))
    (is (= (tc/from-string "2016-06-27") (td/date-from today {:date-from "LAST_WEEK"})))
    (is (= (tc/from-string "2016-07-01") (td/date-from today {:date-from "MONTH"})))
    (is (= (tc/from-string "2016-06-01") (td/date-from today {:date-from "LAST_MONTH"})))
    (is (= (tc/from-string "2016-01-01") (td/date-from today {:date-from "YEAR"})))
    (is (= (tc/from-string "1970-01-01") (td/date-from today {:date-from "ALL"})))
    (is (= (tc/from-string "2016-06-15") (td/date-from today {:date-from "USER_SPECIFIED" :specific-date-from "2016-06-15"})))))

(deftest date-to
  (let [today (tc/from-string "2016-07-10")]
    (is (= (tc/from-string "2016-07-11") (td/date-to today {:date-to "NOW"})))
    (is (= (tc/from-string "2016-07-04") (td/date-to today {:date-to "LAST_WEEK"})))
    (is (= (tc/from-string "2016-07-01") (td/date-to today {:date-to "LAST_MONTH"})))
    (is (= (tc/from-string "9999-01-01") (td/date-to today {:date-to "ALL"})))
    (is (= (tc/from-string "2016-06-15") (td/date-to today {:date-to "USER_SPECIFIED" :specific-date-to "2016-06-15"})))))

(deftest ticket-details
  (with-redefs [clj-http.client/get
                (fn [url]
                  (case url
                    "http://foo/1" {:body "{}"}
                    "http://foo/1234" {:body "{\"html_url\": \"foo\"}"}
                    (throw (Exception.))))]
    (diffeq {:link "" :title "" :state ""} (td/ticket-details "http://foo/%d" 1))
    (diffeq {:link "" :title "" :state ""} (td/ticket-details "http://foo/%d" -1))
    (diffeq {:link "foo" :title "" :state ""} (td/ticket-details "http://foo/%d" 1234))))
