(ns user.state
  "Holds state to be preserved across repl reloads."
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all disable-reload!]]))

(disable-reload! *ns*)

(def system
  "A Var containing an object representing the application under
  development."
  nil)
