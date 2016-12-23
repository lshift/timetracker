(ns time-tracker.sql.user
  (:require
   [yesql.core :refer [defqueries]]))

(defqueries "sql/user.sql")

(def queries {:list list-entities
              :exists exists
              :create create<!
              :get get-entity
              :update update!
              :delete delete!})
