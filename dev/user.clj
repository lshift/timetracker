(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer [javadoc]]
   [clojure.pprint :refer [pprint]]
   [clojure.reflect :refer [reflect]]
   [clojure.repl :refer [apropos dir doc find-doc pst source]]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   [clojure.edn :as edn]
   [com.stuartsierra.component :as component]
   [cemerick.pomegranate]
   [user.state :refer [system]]
   [time-tracker.environ :as environ]))

(defn init
  "Constructs the current development system."
  []
  (alter-var-root
   #'system
   (constantly (environ/system))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system component/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop s)))))

(defn go
  "Initializes and starts the system running."
  []
  (init)
  (start)
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))

(defonce tests-to-run (atom ""))

(defn run-tests []
  (test/run-all-tests @tests-to-run))

(defn reload-test
  ([] (reload-test #"^time-tracker\..*-test$"))
  ([tests]
   (stop)
   (reset! tests-to-run tests)
   (prn ::tests @tests-to-run)
   (refresh :after 'user/run-tests)))

(defn reload-deps []
  (let [project (->> "project.clj" slurp read-string)
        deps (->> project (drop 3) (apply hash-map) :dependencies)
        repos (merge cemerick.pomegranate.aether/maven-central
                     {"clojars" "http://clojars.org/repo"})]
    (cemerick.pomegranate/add-dependencies
     :repositories repos :coordinates deps)))
