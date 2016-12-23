(ns time-tracker.sql.project
  (:require
   [yesql.core :refer [defqueries]]))

(defqueries "sql/project.sql")

(def queries {:list list-entities
              :exists exists
              :create create<!
              :get get-entity
              :update update!
              :delete delete!})
