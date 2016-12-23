(ns time-tracker.main
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [time-tracker.environ :refer [system]]
            [clojure.tools.logging :as log]
            [clojure.tools.nrepl.server :as nrepl]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:gen-class))

(defonce running-system (atom nil))

(defn add-shutdown-hook! [^Runnable f]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. f)))

(defn logged-shutdown []
  (log/info ::shutting-down)
  (swap! running-system component/stop)
  (log/info ::shutdown-done))

(defn run-forever []
  (let [forever (java.util.concurrent.Semaphore. 0)]
    (.acquire forever)))

(defn restart
  []
  (log/info ::restarting)
  (swap! running-system #(do (when % (component/stop %))
                             (component/start (system)))))

(defn -main [& argv]
  (log/info ::starting)
  ;; We start an nrepl outside of the system as we don't want it to go down
  ;; when we remotely restart the component system
  (when-let [port (:nrepl-port env)]
    (log/info ::running-nrepl port)
    (nrepl/start-server :bind "0.0.0.0" :port (Integer/parseInt port)))

  (restart)
  (add-shutdown-hook! logged-shutdown)

  (log/info ::running)
  (run-forever))
