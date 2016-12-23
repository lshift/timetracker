(ns time-tracker.time-sheet-events
  (:require [cljs.core.async :as async
             :refer [<! >! chan close! sliding-buffer put! alts!]]
            [time-tracker.date-time :as dt]
            [time-tracker.event-handling :refer [process-messages]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(def +new-task+
  {:id :time-tracker/new-task
   :project_id nil
   :activity_id nil
   :bug ""
   :description ""})

(defn- fraction-of-day-to-date [date tod]
  (let [quarters (.round js/Math (* 24 4 tod))
        hour (quot quarters 4)
        minute (rem (* quarters 15) 60)]
    (doto (js/Date. (.valueOf date))
      (.setHours hour)
      (.setMinutes minute))))

(defmulti handle-ui-event!
  (fn [state-root commands-ch loop-state event]
    (prn ::handle-ui-event!:discriminator (:type event))
    (:type event)))

(defn valid-task-info? [{:keys [project_id activity_id] :as info}]
  (boolean (and project_id activity_id)))

(defn extent-to-times [current-date start-x end-x]
  (sort (map (partial fraction-of-day-to-date current-date)
             [start-x end-x])))

(defmethod handle-ui-event! :time-selected
  [state-root commands-ch
   {:keys [] :as loop-state}
   {:keys [id from to] :as event}]
  (go
    (cond
      (or (= :time-tracker/idle id) (number? id))
      (>! commands-ch
          {:command :commit-time! :id id :from from :to to})

      (= :time-tracker/new-task id)
      (let [{:keys [new-task timebar-meta]} @state-root
            {:keys [reset-on-new-task]} timebar-meta
            new-info (select-keys
                      new-task [:activity_id :project_id :bug :description])]
        (when (valid-task-info? new-info)
          (>! commands-ch {:command :new-task! :from from :to to :info new-info})
          (swap! state-root update-in [:new-task] #(if reset-on-new-task +new-task+ %))))

      :else (throw (ex-info (str "Unrecognised-id: " (pr-str id))
                            {:id id, :event event})))

    loop-state))

(defmethod handle-ui-event! :field-changed
  [state-root commands-ch loop-state {:keys [id attribute value]}]
  (go
    (let [path (if (number? id) [:tasks id attribute] [:new-task attribute])]
      (swap! state-root update-in path (constantly value))
      loop-state)))

(defmethod handle-ui-event! :edit-requested
  [state-root commands-ch loop-state {:keys [id]}]
  (go
    (swap! state-root update-in [:editing?] #(-> % set (conj id)))
    loop-state))

(defmethod handle-ui-event! :commit-edits!
  [state-root commands-ch loop-state {:keys [id]}]
  (go
    (when (number? id)
      (let [new-info (get-in @state-root [:tasks id])]
        (when (valid-task-info? new-info)
          (swap! state-root update-in [:editing?] #(-> % set (disj id)))
          (>! commands-ch {:command :update-task-info! :id id :info new-info}))
        loop-state))))

(defmethod handle-ui-event! :go-to-date
  [state-root commands-ch loop-state {:keys [which]}]
  (go
    (>! commands-ch {:command :go-to-date :which which})
    loop-state))

(defmethod handle-ui-event! :user-selected
  [state-root commands-ch loop-state {:keys [user-name]}]
  (go
    (>! commands-ch {:command :change-user :user-name user-name})
    loop-state))

(defmethod handle-ui-event! :show-time-sheet
  [state-root commands-ch loop-state {:keys [user-name date]}]
  (go
    (>! commands-ch {:command :go-to-date :which date})
    (>! commands-ch {:command :change-user :user-name user-name})
    loop-state))

(defmethod handle-ui-event! :show-root
  [state-root commands-ch loop-state {:keys []}]
  (go
    (>! commands-ch {:command :go-to-root})
    loop-state))

(defn process-ui-events! [events-ch commands-ch state-root]
  (let [handler-fn (partial handle-ui-event! state-root commands-ch)]
    (process-messages events-ch handler-fn
                      {:on-close (fn [_] (close! commands-ch))})))
