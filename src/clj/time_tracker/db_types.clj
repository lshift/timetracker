(ns time-tracker.db-types
  (:require [clojure.java.jdbc :as j]
            [clj-time.format :as f])
  (:import [org.joda.time LocalDate DateTime]))

(extend-protocol j/ISQLValue
  org.joda.time.LocalDate
  (sql-value [val]
    (f/unparse-local-date (f/formatters :date) val))
  DateTime
  (sql-value [val]
    (f/unparse (f/formatters :date-time) val)))
