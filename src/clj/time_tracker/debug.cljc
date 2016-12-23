(ns time-tracker.debug
  "Better than println for debugging in development. These macros are designed
  to be quick and easy to add to any form. Available in CLJ and CLJS. See docs
  for ? and ??.

    ;; Remember to use :refer-macros in ClojureScript
    (ns whatever
      (:require [time-tracker.debug :refer-macros [? ??]]))"
  (:require #?(:clj  [clojure.pprint :refer [pprint]]
               :cljs [cljs.pprint :refer [pprint]])
            #?(:clj [clojure.tools.logging :as log]))
  )

(defn pprint-str
  [o]
  (with-out-str (pprint o)))

(defn debug-log
  [& args]
  (#?(:clj log/debug :cljs js/console.log)
   (apply str args)))

;; N.B. the macros are wrapped in :clj reader conditionals as "ClojureScript
;; macros" are actually Clojure macros expanded during the compilation stage
;; (and compilation is Clojure-based).

#?(:clj
   (defmacro ?
     "Macro to debug any arbitrary form. Takes the body of an expression, logs the
     expression and its result. Returns the result of the expression.

         (foo 'bar)   ;; call foo
         (? foo 'bar) ;; call foo and log the expression and result

         (let [x 2 y 4 z 5] (? * x y z))
         ; (* x y z) => 40"
     [& body]
     {:pre [(symbol? (first body))]}
     `(let [res# ~body]
        (debug-log '~body " => " (pprint-str res#))
        res#)))

#?(:clj
   (defmacro ??
     "Macro to *really* debug any arbitrary form. Takes the body of an
     expression, logs the expression, the lexical environment (let bindings)
     and the expression's result. Returns the result of the expression.

         (foo 'bar)    ;; call foo
         (?? foo 'bar) ;; call foo and log the lexical bindings, the expression and result

         (let [x 2 y 4 z 5] (?? * x y z))
         ; Expression:  (* x y z)
         ; Environment: {x 2, y 4, z 5}
         ; Result:      40"
     [& body]
     {:pre [(symbol? (first body))]}
     (let [lexical-ks (if-let [cljs-locals (:locals &env)]
                        (keys cljs-locals)
                        (keys &env))]
       `(let [res# ~body]
          (debug-log
           \newline
           "Expression:  " '~body \newline
           "Environment: " (zipmap '~lexical-ks (list ~@lexical-ks)) \newline
           "Result:      " (pprint-str res#))
          res#))))
