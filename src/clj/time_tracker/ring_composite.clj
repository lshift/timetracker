(ns time-tracker.ring-composite
  (:require  [com.stuartsierra.component :as component]
             [ring.middleware.params :refer [wrap-params]]
             [time-tracker.ring :as ring]
             [clojure.tools.logging :as log]))

(defrecord OverlaidHandlers [order]
  ring/RingRequestHandler
  (request-handler [self]
    (when-let [invalid-handlers (seq (filter #(not (satisfies? ring/RingRequestHandler (get self %))) order))]
      (throw (ex-info "All child handlers must be present and implement RingRequestHandler"
                      {:invalid-keys invalid-handlers
                       :invalid-handlers (select-keys self invalid-handlers)})))
    (let [children (map #(get self %) order)
          handlers (map ring/request-handler children)]
      (fn [req]
        (some #(% req) handlers)))))

(defn overlay [ordering]
  (map->OverlaidHandlers {:order ordering}))
