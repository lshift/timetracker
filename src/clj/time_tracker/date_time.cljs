(ns time-tracker.date-time
  (:require [cljs-time.format :as fmt]
            [cljs-time.coerce :as coerce]
            [cljs-time.core :as time]
            [cljs-time.extend] ; lets us compare Dates and DateTimes
            [time-tracker.time-zones :as tz]
            [goog.date])
  (:import [goog.date Date DateTime UtcDateTime Interval weekDay]))

(def day-of-week-name
  {0 "Sun"
   1 "Mon"
   2 "Tue"
   3 "Wed"
   4 "Thu"
   5 "Fri"
   6 "Sat"})

(defn- pad [n]
  (if (< n 10)
    (str "0" n)
    (str n)))

(extend-type Date
  coerce/ICoerce
  (to-date-time [dt]
    (doto (UtcDateTime.)
      (.set dt)))
  IHash
  (-hash [dt]
    (hash (.valueOf dt)))

  time/DateTimeProtocol
  (year [this] (.getYear this))
  (month [this] (inc (.getMonth this)))
  (day [this] (.getDate this))
  (day-of-week [this] (let [d (.getDay this)] (if (= d 0) 7 d)))
  (hour [this] (.getHours this))
  (minute [this] (.getMinutes this))
  (second [this] (.getSeconds this))
  (milli [this] (.getMilliseconds this))
  (after? [this that] (> (.getTime this) (.getTime that)))
  (before? [this that] (< (.getTime this) (.getTime that)))
  (plus- [this period] ((time/period-fn period) + this))
  (minus- [this period] ((time/period-fn period) - this)))

(defn today []
  (Date.))

(defn now []
  (DateTime.))

(defn to-iso8601 [time]
  (fmt/unparse (fmt/formatters :date-time) (coerce/to-date-time time)))

(defn to-basic-iso8601 [time]
  (fmt/unparse (fmt/formatters :basic-date-time-no-ms) (coerce/to-date-time time)))

(defn to-basic-date [dt]
  (fmt/unparse (fmt/formatters :basic-date) (coerce/to-date-time dt)))

(defn tz-offset [date]
  (.getTimezoneOffset date))

(defn date-str [date]
  "Returns the date portion of date as it would be in Local Time."
  (let [d (coerce/from-long (.valueOf date))
        offset (time/minus d (time/minutes (tz-offset date)))]
    (fmt/unparse (fmt/formatters :date) offset)))

;; Returns a UTC date time which pretends to be the local time in the given
;; zone corresponding to the given UTC instant.
(defn as-local-time
  ([d tz]
   (assert (= 0 (.getTimezoneOffset d)))
   (time/minus d (time/minutes (.getOffset tz d)))))

(defn day-of-week [date]
  (-> date
      .getDay
      day-of-week-name))

(defn prettify-date [date]
  (let [d (date-str date)]
    (str d " " (day-of-week date))))

(defn day-add [d n]
  (doto (.clone d)
    (.add (Interval. Interval.DAYS n))))

(defn day-before [d]
  (day-add d -1))

(defn day-after [d]
  (day-add d +1))

(defn start-of-week
  ([date] (start-of-week date weekDay.MON))
  ([date weekday]
   (let [d (doto (.clone date) (.setFirstDayOfWeek weekday))]
     (day-add date (- (.getWeekday d))))))

(defn parse-int [s]
  (js/window.parseInt s 10))

(defn parse-date [time]
  (let [res (Date. 0 0 0)]
    (if (goog.date/setIso8601DateTime res time)
      res
      (throw (ex-info (str "Could not parse date: " time) {:time-str time})))))

(defn parse-timestamp [time]
  (let [res (DateTime. 0 0 0)]
    (if (goog.date/setIso8601DateTime res time)
      res
      (throw (ex-info (str "Could not parse date: " time) {:time-str time})))))

;; This allows us to use value equality on goog.date.Date instances. cljs-time
;; already provides this for goog.date.DateTime.
(extend-type Date
  IEquiv
  (-equiv [o other]
    (and (instance? Date other)
         (= (.valueOf o) (.valueOf other)))))

;; Returns a UTC timestamp which describes the instant the fields of orig as
;; if it were in tz. This almost requires an iterative approach, as we take
;; a guess and improve it. When the offset returned by our guessed point is
;; stable, we can use that. However, if it would oscillate, then we want to
;; pick the time with the most positive offset.
;;
;; Honestly, I'm not sure why. But my tests seem to say that it is okay.

(defn from-time-zone-as-utc [tz orig]
  (let [as-utc (coerce/to-date-time orig)
        offset0 (.getOffset tz as-utc)
        guess (time/plus as-utc (time/minutes offset0))
        offset1 (.getOffset tz guess)
        guess2 (time/plus as-utc (time/minutes offset1))
        offset2 (.getOffset tz guess2)]
    (cond
      (= offset0 offset1) guess
      (= offset1 offset2) guess2
      :else (if (> offset0 offset1) guess guess2))))
