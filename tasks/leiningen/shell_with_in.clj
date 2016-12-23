(ns leiningen.shell-with-in
	(:require [clojure.java.shell :only [sh] :as shell])
)

(defn shell-with-in [project in-file & args]
	(with-in-str (slurp in-file)
		(apply shell/sh args)
	)
)