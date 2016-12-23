(ns time-tracker.jetty
  (:require  [com.stuartsierra.component :as component]
             [ring.adapter.jetty :refer [run-jetty]]
             [time-tracker.ring :as ring]
             [clojure.tools.logging :as log]))

(defrecord JettyServer [port web-app server]
  component/Lifecycle
  (start [component]
    (log/info ::starting-jetty :port port)
    (let [server (run-jetty (ring/request-handler web-app) {:port port :join? false})
          port' (->> server (.getConnectors) (some (memfn getLocalPort)))]
      (log/info ::started-jetty server :on-port port')
      (assoc component :server server :port port')))

  (stop [component]
    (when server
      (.stop server))
    (assoc component :server nil)))

(defn jetty-server
  [port]
  (map->JettyServer {:port port}))
