(ns time-tracker.time-zones
  (:require-macros [time-tracker.time-zones :refer (timezones-from-resources)])
  (:import [goog.i18n TimeZone]))

(def +zone-data+ (timezones-from-resources))

(defn time-zone-for-id [id]
  (when-not (contains? +zone-data+ id)
    (throw (ex-info (str "Unknown timezone: " id) {:timezone id})))
  (when-let [zinfo (+zone-data+ id)]
    (-> zinfo
        TimeZone.createTimeZone)))

(defn time-zone-ids []
  (keys +zone-data+))
