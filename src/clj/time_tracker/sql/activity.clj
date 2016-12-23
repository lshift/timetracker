(ns time-tracker.sql.activity
  (:require
   [yesql.core :refer [defqueries]]))

(defqueries "sql/activity.sql")

(def queries {:list list-entities
              :exists exists
              :create create<!
              :get get-entity
              :update update!
              :delete delete!})
