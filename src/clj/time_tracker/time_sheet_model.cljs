(ns time-tracker.time-sheet-model
  (:require [cljs.core.async :as async
             :refer [<! >! chan close! sliding-buffer put! alts!]]
            [time-tracker.server :as server]
            [time-tracker.date-time :as dt]
            [time-tracker.time-zones :as tz]
            [time-tracker.preferences :as prefs]
            [time-tracker.event-handling :refer [process-messages]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]
                   [time-tracker.async :refer [try<!]]))

(defn reset-projects! [state-root projects]
  (swap! state-root assoc-in [:projects] projects))

(defn reset-activities! [state-root activities]
  (swap! state-root assoc-in [:activities] activities))

(defn reset-users! [state-root users]
  (swap! state-root assoc-in [:users] users))

(defn as-index-tuple [{:keys [id] :as task}]
  [id task])

(defn parse-time-json [{:keys [start_time end_time] :as task}]
  (assoc task
         :start_time (dt/parse-timestamp start_time)
         :end_time (dt/parse-timestamp end_time)))

(defn parse-task-json [task]
  (update-in task [:times] (partial map parse-time-json)))

(defn- parse-tasks [json-resp]
  (->> json-resp
       (map parse-task-json)
       (map as-index-tuple)
       (into (sorted-map))))

(defn reset-tasks! [state-root tasks]
  (let [parsed (parse-tasks tasks)]
    (swap! state-root (comp #(assoc-in % [:tasks] parsed)
                            #(assoc-in % [:updating?] false)))))

(defn fetch-time-sheet! [state-root config]
  (go
    (let [{:keys [current-user current-date prefs]} @state-root
          {:keys [timezone]} prefs
          tz (tz/time-zone-for-id (or timezone "Europe/London"))]
      (when (and current-user current-date)
        (reset-tasks! state-root
                      (<! (server/fetch-time-sheet-for config current-user current-date tz)))))))

(defmulti process-command! (fn [state-root command config] (:command command)))

(defmethod process-command! :commit-time! [state-root {:keys [id from to]} config]
  (go
    (let [{:keys [current-user]} @state-root]
      (swap! state-root assoc-in [:updating?] true)
      (<! (if (= :time-tracker/idle id)
            (server/mark-idle! config current-user from to)
            (server/post-timebar! config current-user id from to)))
      (<! (fetch-time-sheet! state-root config)))))

(defmethod process-command! :new-task!  [state-root {:keys [from to info]} config]
  (go
    (let [{:keys [current-user]} @state-root]
      (swap! state-root assoc-in [:updating?] true)
      (let [new-id (try<! (server/create-task! config info))
            resp   (try<! (server/post-timebar! config current-user new-id from to))]
        (try<! (fetch-time-sheet! state-root config))))))

(defmethod process-command! :update-task-info! [state-root {:keys [id info]} config]
  (go
    (let [{:keys [current-user current-date]} @state-root]
      (swap! state-root assoc-in [:updating?] true)
      (<! (server/update-task-info! config current-user current-date id info))
      (<! (fetch-time-sheet! state-root config)))))

(defmethod process-command! :go-to-date [state-root {:keys [which]} config]
  (go
    (swap! state-root #(as-> % root
                             (assoc-in root [:updating?] true)
                             (update-in root [:current-date] (condp = which
                                                               :back dt/day-before
                                                               :next dt/day-after
                                                               (constantly which)))))
    (<! (fetch-time-sheet! state-root config))))

(defmethod process-command! :change-user
  [state-root {:keys [user-name] :as command} config]
  (go
    (swap! state-root
           #(let [user (get-in % [:users user-name])]
              (if user
                (assoc-in % [:current-user] user)
                (throw (str "No user found for id:" user-name ", "
                            "known: " (pr-str (keys (:users %))))))))
    (<! (fetch-time-sheet! state-root config))))

(defmethod process-command! :go-to-root
  [state-root command config]
  (go
    (swap! state-root assoc :current-user nil)))

(defn model-loop [commands-ch state-root config]
  (go
    (let [projects-ch (server/fetch-projects config)
          activities-ch (server/fetch-activities config)
          users-ch (server/fetch-users config)
          handler-fn (fn [state command] (process-command! state-root command config))]
      (reset-projects! state-root (<! projects-ch))
      (reset-activities! state-root (<! activities-ch))
      (reset-users! state-root (<! users-ch))
      (<! (process-messages commands-ch handler-fn)))))
