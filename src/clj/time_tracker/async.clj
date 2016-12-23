(ns time-tracker.async)

(defmacro try<!
  "Behaves as per <!, but checks if response values are of the form
  `{:state :error :errpr ...}` and throws an ex-info if so."
  [ch-expr]
  `(let [ch# ~ch-expr
         v#  (~'cljs.core.async/<! ch#)]
     (if-not (= :error (:state v#))
       v#
       (do
         (prn "Error value returned from channel"
              {:channel-expression '~ch-expr
               :error              (:error v#)})
         (throw (ex-info "Error value returned from channel"
                         {:channel-expression '~ch-expr
                          :error              (:error v#)}))))))
