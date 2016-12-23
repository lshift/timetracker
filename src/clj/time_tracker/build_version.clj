(ns time-tracker.build-version
  (:require [clojure.java.io :as io]))

;; File to be replaced when building (i.e. on TeamCity)
(def version (delay (slurp (io/resource "timetracker-version"))))

