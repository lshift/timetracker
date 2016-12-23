(ns time-tracker.teamcity
  (:require
   [cljs.test :as test]))

(defn escape
  [s]
  (clojure.string/replace s #"['|\n\r\[\]]"
                          (fn [x]
                            (cond (= x "\n") "|n"
                                  (= x "\r") "|r"
                                  :else (str "|" x)))))

(defmethod cljs.test/report [:cljs.test/default :begin-test-ns] [m]
  (println (str "##teamcity[testSuiteStarted name='" (ns-name (:ns m)) "']")))

(defmethod cljs.test/report [:cljs.test/default :end-test-ns] [m]
  (println (str "##teamcity[testSuiteFinished name='" (ns-name (:ns m)) "']")))

(defn test-name [m]
  (apply str (map #(:name (meta %)) (:testing-vars (test/get-current-env)))))

(defn fail-msg [event]
  (str (if (:message event) (str :message " " (:message event) "\n") "")
       :expected " " (:expected event) "\n"
       :actual " " (:actual event)))

(defmethod cljs.test/report [:cljs.test/default :begin-test-var] [m]
  (println (str "##teamcity[testStarted name='" (test-name m)  "' captureStandardOutput='true']")))
(defmethod cljs.test/report [:cljs.test/default :end-test-var] [m]
  (println (str "##teamcity[testFinished name='" (test-name m)  "' captureStandardOutput='true']")))

(defmethod cljs.test/report [:cljs.test/default :error] [m]
  (cljs.test/inc-report-counter! :error)
  (println (str "##teamcity[testFailed name='" (test-name m) "' message='" (escape (fail-msg m)) "']")))
