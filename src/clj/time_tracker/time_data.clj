(ns time-tracker.time-data
  (:require  [com.stuartsierra.component :as component]
             [liberator.core :refer [resource defresource]]
             [liberator.dev :refer [wrap-trace]]
             [ring.middleware.params :refer [wrap-params]]
             [ring.util.response :refer [resource-response]]
             [compojure.core :refer [routes ANY GET]]
             [compojure.route :as route]
             [clojure.java.jdbc :as j]
             [clojure.java.io :as io]
             [yesql.core :refer [defquery]]
             [time-tracker.ring :as ring]
             [clojure.data.json :as json]
             [clj-time.core :as t]
             [clj-time.format :as f]
             [clj-time.coerce :as tc]
             [clojure.tools.logging :as log]
             [time-tracker.db-types]))

(defquery insert-task<! "sql/insert-task.sql")
(defquery task-by-details "sql/query-task-by-details.sql")
(defquery query-task-time-by-user-period "sql/query-task-time-by-user-period.sql")
(defquery insert-time! "sql/insert-time.sql")
(defquery update-task-time! "sql/update-task-time-id.sql")
(defquery query-existing-task-time "sql/query-existing-task-time.sql")
(defquery query-conflicting-task-time "sql/query-conflicting-task-time.sql")
(defquery delete-task-time! "sql/delete-task-time.sql")
(defquery update-task-time-interval! "sql/update-task-time-interval.sql")
(defquery query-recent-tasks-by-user "sql/query-recent-tasks-by-user.sql")
(defquery is-active-user? "sql/is-active-user.sql")

(defn joda-from-sql [^java.sql.Timestamp ts]
  (when-not (nil? ts)
    (tc/from-long (.getTime ts))))

(defn- parse-sql-date-times [row]
  (cond-> row
    (:start_time row) (update-in [:start_time] joda-from-sql)
    (:start_time row) (update-in [:end_time] joda-from-sql)))

(defn with-defaults [item defaults]
  (merge defaults item))

(defn ensure-task! [{:keys [conn] :as db}
                    task]
  (let [{:keys [project_id activity_id bug description]}
        (with-defaults task {:bug "" :description ""})]
    (j/with-db-transaction [trans conn {:isolation :serializable}]
      (if-let [rows (seq (task-by-details {:project_id project_id :activity_id activity_id :bug bug :description description} {:connection trans}))]
        (-> rows first :id)
        (let [row (insert-task<! {:project_id project_id :activity_id activity_id :bug bug :description description} {:connection trans})]
          (:id row))))))

(defn- match-ordering [[start_match end_match] [start_ord end_ord]]
  (and (start_match start_ord) (end_match end_ord)))

(defn remove-conflicts [trans user start_time end_time]
  (log/info ::remove-conflicts [user start_time end_time])
  (doseq [row (->> (query-conflicting-task-time {:start_time start_time :end_time end_time :user_name user} {:connection trans})
                   (map parse-sql-date-times))]
    (log/info ::conflicting! row)
    (condp match-ordering
           [(compare (:start_time row) start_time) (compare (:end_time row) end_time)]
      [#{0 1} #{-1 0}]
      (delete-task-time! {:task_time_id (:id row)} {:connection trans})
      [#{-1 0} #{-1 0}]
      (update-task-time-interval! {:start_time (:start_time row) :end_time start_time :task_id (:id row)} {:connection trans})
      [#{0 1} #{0 1}]
      (update-task-time-interval! {:start_time end_time :end_time (:end_time row) :task_id (:id row)} {:connection trans})
      [#{-1} #{1}]
      (do
        (log/info ::splitting row :with start_time end_time)
        (update-task-time-interval! {:start_time (:start_time row) :end_time start_time :task_id (:id row)} {:connection trans})
        (insert-time! {:task_id (:task_id row) :start_time end_time :end_time (:end_time row) :user_name user} {:connection trans})))))

(defn record-time! [{:keys [conn]} id user start_time end_time]
  (j/with-db-transaction [trans conn {:isolation :serializable}]
    (remove-conflicts trans user start_time end_time)
    (when-let [existing-task (first (query-existing-task-time {:start_time start_time :end_time end_time :user_name user} {:connection trans}))]
      (let [deleted-time-count (delete-task-time! {:task_time_id (:id existing-task)} {:connection trans})]
        (when-not (= 1 deleted-time-count)
          (throw (ex-info "Expected to only delete one time entry" {:count deleted-time-count})))))
    (insert-time! {:task_id id :start_time start_time :end_time end_time :user_name user} {:connection trans})))

(defn migrate-time-period! [{:keys [conn]} id new-id user start_time end_time]
  (update-task-time! {:new_task_id new-id :old_task_id id :start_time start_time :end_time end_time :user_name user} {:connection conn}))

(defn mark-time-idle! [{:keys [conn]} user start_time end_time]
  (j/with-db-transaction [trans conn {:isolation :serializable}]
    (remove-conflicts trans user start_time end_time)))

(defn list-tasks-between [{:keys [conn] :as db} user start end previous-task-period]
  (j/with-db-transaction [trans conn {:isolation :serializable}]
    (let [task-cols [:id :project_id :activity_id :bug :description]
          key-fn (apply juxt task-cols)
          current-time (query-task-time-by-user-period {:user_name user :start_time start :end_time end} {:connection trans})
          recent-tasks (query-recent-tasks-by-user {:user_name user :start_time (t/minus start previous-task-period) :end_time start} {:connection trans})
          grouped (->> (concat current-time recent-tasks)
                       (map parse-sql-date-times)
                       (group-by key-fn))]
      (log/info ::current-time current-time ::recent recent-tasks)
      (for [[task-info times] grouped]
        (let [task-time (->> times
                             (map #(select-keys % [:start_time :end_time]))
                             (filter (comp not empty?)))]
          (-> (zipmap task-cols task-info)
              (assoc :times task-time)))))))

(defn active-user? [{:keys [conn] :as db} user-name]
  (-> (is-active-user? {:user_name user-name} {:connection conn})
      first
      :activep))
