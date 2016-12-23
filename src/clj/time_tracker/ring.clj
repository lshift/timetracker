(ns time-tracker.ring)

(defprotocol RingRequestHandler
  (request-handler [_]
    "Returns a ring request-handler function. Called by the webserver on startup."))
