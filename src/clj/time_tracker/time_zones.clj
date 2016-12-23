(ns time-tracker.time-zones
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:import [java.util Properties]))

(def +tz-properties+ "time_tracker/TimeZoneConstants.properties")

(defn read-tz-properties [propfile]
  (let [rdr (-> propfile io/resource io/reader)]
    (doto (java.util.Properties.)
      (.load rdr))))

(defn- read-json [input]
  (json/read-str input :key-fn keyword))

(defn as-js-obj-sexp [m]
  (cons 'cljs.core/js-obj
        (mapcat
         (fn [[k v]]
           (list (name k)
                 (cond
                   (vector? v) (cons 'cljs.core/array v)
                   :else v)))
         m)))

(defn as-time-zone-map [props]
  (let [parsed (->> props vals (map read-json))]
    (into {} (map (juxt :id as-js-obj-sexp) parsed))))

(defmacro timezones-from-resources []
  (->  +tz-properties+
       read-tz-properties
       as-time-zone-map))
