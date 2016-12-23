(ns time-tracker.event-handling
  (:require [cljs.core.async :as async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn process-messages [events-ch handler & [opts]]
  (let [{:keys [init-state on-close]} opts]
    (go
      (loop [loop-state init-state]
        (if-let [event (<! events-ch)]
          (recur (<! (handler loop-state event)))
          (when on-close
            (on-close loop-state)))))))
