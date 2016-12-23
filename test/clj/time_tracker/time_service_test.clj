(ns time-tracker.time-service-test
  (:require  [clojure.test :refer [deftest testing is]]
             [clojure.data.json :as json]
             [clojure.java.jdbc :as j]
             [ring.mock.request :refer [request body content-type header]]
             [clj-time.core :as t]
             [clj-time.coerce :as tc]
             [clj-time.format :as f]
             [clojure.tools.logging :as log]
             [time-tracker.test-systems :refer [with-system ok? json? created? no-content? json-body empty-table! random-string get-location-header]]))

(defn post-response-fn [base handler]
  (let [name (random-string)]
    (->
     (request :post base)
     (content-type "application/json")
     (body (json/write-str {:name name}))
     (handler))))

(defn plain-item-returns-listing [kind path active-check]
  (with-system
    (let [response (handler (request :get path))]
      (is (ok? response))
      (is (json? response))
      (let [parsed (json-body response)]
        (is (> (count parsed) 0))
        (is (every? (comp integer? :id) parsed))
        (is (every? (comp string? :name) parsed))
        (if active-check
          (is (every? :active parsed)))
        (is (= (count (into #{} (map :id parsed))) (count parsed)))))))

(defn plain-item-make [kind]
  (with-system
    (let [post-response (post-response-fn (str "/" kind) handler)]
      (log/debug ::post-response post-response)
      (is (created? post-response)))))

(defn plain-item-get [kind]
  (with-system
    (let [post-response (post-response-fn (str "/" kind) handler)
          location (get-location-header post-response)
          get-response (->
                        (request :get location)
                        (handler))]
      (log/debug ::get-response get-response)
      (is (ok? get-response))
      (is (json? get-response))
      (let [parsed (json-body get-response)]
        (is (string? (:name parsed)))
        (is (integer? (:id parsed)))))))

(defn plain-item-change [kind]
  (with-system
    (let [post-response (post-response-fn (str "/" kind) handler)
          location (get-location-header post-response)
          new-name (random-string)
          put-response (->
                        (request :put location)
                        (content-type "application/json")
                        (body (json/write-str {:name new-name :active false}))
                        (handler))]
      (log/debug ::new-name new-name)
      (log/debug ::location location)
      (log/debug ::put-response put-response)
      (is (no-content? put-response)))))

(defn plain-item-can-delete [kind]
  (with-system
    (let [post-response (post-response-fn (str "/" kind) handler)
          location (get-location-header post-response)
          delete-response (->
                           (request :delete location)
                           (handler))]
      (log/debug ::delete-response delete-response)
      (is (no-content? delete-response)))))

(defn plain-item-cant-delete [kind]
  (with-system
    (let [delete-response (->
                           (request :delete (format "/%s/200000" kind))
                           (handler))]
      (log/debug ::delete-response delete-response)
      (is (= (:status delete-response) 404)))))

(defmacro test-plain-items [kind]
  `(do
     (deftest ~(symbol (format "%s-returns-%s-listing" kind kind))
       (plain-item-returns-listing ~kind ~(str "/" kind) true))
     (deftest ~(symbol (format "%s-returns-%s-listing-for-all" kind kind))
       (plain-item-returns-listing ~kind ~(format "/%s?type=all" kind) false))
     (deftest ~(symbol (format "%s-can-make-%s" kind kind))
       (plain-item-make ~kind))
     (deftest ~(symbol (format "%s-can-get-%s" kind kind))
       (plain-item-get ~kind))
     (deftest ~(symbol (format "%s-can-change-%s" kind kind))
       (plain-item-change ~kind))
     (deftest ~(symbol (format "%s-can-delete-%s" kind kind))
       (plain-item-can-delete ~kind))
     (deftest ~(symbol (format "%s-cant-delete-missing-%s" kind kind))
       (plain-item-cant-delete ~kind))))

(test-plain-items "activities")
(test-plain-items "projects")
(test-plain-items "users")

(defn create-task! [handler task]
  (log/debug ::create-task! task)
  (-> (request :post "/time")
      (body task)
      handler))

(defn get-location [response]
  (let [location (-> response :headers (get "Location"))]
    (if (nil? location)
      (throw (Exception. (pr-str response)))
      location)))

(defn valid-task-url? [location]
  (re-find #"^/time/\d+$" location))

(def task-info-1
  {:project_id 1, :activity_id 1, :bug "123", :description "task-info-1"})
(def task-info-2
  {:project_id 2, :activity_id 2, :bug "1234", :description "task-info-2"})
(def task-info-3
  {:project_id 3, :activity_id 2, :bug "456", :description "task-info-3"})

(deftest can-create-a-new-task
  (with-system
    (let [response (create-task! handler task-info-1)]
      (is (= 201 (:status response)))
      (is (valid-task-url? (get-location response))))))

(deftest creates-a-different-task-for-different-details
  (with-system
    (let [response (create-task! handler task-info-1)
          response2 (create-task! handler task-info-2)]
      (is (not= (get-location response) (get-location response2))))))

(deftest returns-the-same-task-for-identical-details
  (with-system
    (let [response (create-task! handler task-info-1)
          response2 (create-task! handler task-info-1)]
      (is (valid-task-url? (get-location response)))
      (is (valid-task-url? (get-location response2)))
      (is (= (-> response :headers (get "Location"))
             (-> response2 :headers (get "Location")))))))

(deftest returns-an-error-when-project-or-activity-is-nil
  (with-system
    (doseq [field #{:project_id :activity_id}]
      (let [response (create-task! handler (assoc task-info-1 field ""))]
        (prn ::response field response)
        (is (= 400 (:status response)))))))

(deftest create-is-okay-when-the-bug-or-description-is-missing
  (with-system
    (doseq [field #{:bug :description}]
      (let [response (create-task! handler (assoc task-info-1 field ""))]
        (prn ::response field response)
        (is (= 201 (:status response)))))))

(deftest create-returns-the-same-task-for-identical-details-when-bug-missing
  (with-system
    (let [task (assoc task-info-1 :bug "")
          response (create-task! handler task)
          response2 (create-task! handler task)]
      (is (valid-task-url? (get-location response)))
      (is (valid-task-url? (get-location response2)))
      (is (= (-> response :headers (get "Location"))
             (-> response2 :headers (get "Location")))))))

(deftest returns-the-same-task-for-identical-details-when-description-missing
  (with-system
    (let [task (assoc task-info-1 :description "")
          response (create-task! handler task)
          response2 (create-task! handler task)]
      (is (valid-task-url? (get-location response)))
      (is (valid-task-url? (get-location response2)))
      (is (= (-> response :headers (get "Location"))
             (-> response2 :headers (get "Location")))))))

(defn task-url-to-id [url]
  (let [[_ id :as r] (re-find #"^/time/(\d+)$" url)]
    id))

(defn record-time-req! [handler id user start end]
  (log/debug ::record-time-req! id user start end)
  (let [req {:user user :start_time start :end_time end}]
    (-> (request :post (format "/time/%s" id))
        (body req)
        handler)))

(defn record-time! [handler id user start end]
  (log/debug ::record-time! id user start end)
  (let [resp (record-time-req! handler id user start end)]
    (is (= 201 (:status resp)))))

(defn move-time-on-day! [handler id user date task]
  (log/debug ::move-time-on-day! id user date task)
  (let [req (assoc task :id id :user user :start date :end (t/plus date (t/days 1)))
        resp  (-> (request :post "/time")
                  (body req)
                  handler)]
    (is (= 201 (:status resp)) "Bad status when moving time")
    (is (not= (task-url-to-id (get-location resp)) id))))

(defn delete-time! [handler user start end]
  (log/debug ::delete-time! user start end)
  (let [req {:user user :start_time start :end_time end}
        resp (-> (request :delete "/time")
                 (body req)
                 handler)]
    (clojure.pprint/pprint resp)
    (is (= 204 (:status resp)))))

(defn- json-value-fn [k v]
  (if (and v (#{:start_time :end_time} k))
    (f/parse (f/formatters :date-time) v)
    v))

(defn list-time
  ([handler user date] (list-time handler user date (t/hours 0)))
  ([handler user date ^org.joda.time.Hours recent-tasks]
   (let [start-inst (tc/to-date-time date)
         end-inst (t/plus start-inst (t/days 1))
         fmt (f/formatters :date-time)
         query {:user user :start (f/unparse fmt start-inst)
                :end (f/unparse fmt end-inst)
                :recent_hours (.getHours recent-tasks)}
         resp (-> (request :get "/time" query)
                  handler)]
     (clojure.pprint/pprint resp)
     (is (= 200 (:status resp)) "Bad status when listing time")
     (is (json? resp) "Time listing response is not json")
     (json/read-str (:body resp) :key-fn keyword :value-fn json-value-fn))))

(def user "paulj")
(def other-user "BenJ")
(def non-user "notauser")

(deftest can-record-time
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))]
        (is (= 1 (count tasks)))
        (let [task (first (filter #(= (:description %) (:description task-info-1)) tasks))]
          (is task)
          (is (= [{:start_time (t/date-time 2011 8 8 13 0 0)
                   :end_time (t/date-time 2011 8 8 14 0 0)}]
                 (:times task))))))))

(deftest start_time_before_end_time
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)]
      (is (= 201 (:status (record-time-req! handler task1-id user (t/date-time 2016 10 10 14 0 0) (t/date-time 2016 10 10 14 15 0)))))
      (is (= 400 (:status (record-time-req! handler task1-id user (t/date-time 2016 10 10 14 0 0) (t/date-time 2016 10 10 14 0 0)))))
      (is (= 400 (:status (record-time-req! handler task1-id user (t/date-time 2016 10 10 14 0 0) (t/date-time 2016 10 10 13 45 0))))))))

(deftest error-for-nonexistent-user
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (let [resp (record-time-req!
                  handler task1-id non-user
                  (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))]
        (is (= 400 (:status resp)))))))

(deftest does-not-modify-non-conflicting-records
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)
          task2-id (-> (create-task! handler task-info-2) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (record-time! handler task1-id user (t/date-time 2011 8 8 14 00 00) (t/date-time 2011 8 8 15 00 00))
      (record-time! handler task2-id user (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))]
        (is (= 2 (count tasks)))
        (let [task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))
              task-2 (first (filter #(= (:description %) (:description task-info-2)) tasks))]
          (is task-1)
          (is (= #{{:start_time (t/date-time 2011 8 8 12 0 0)
                    :end_time (t/date-time 2011 8 8 13 0 0)}
                   {:start_time (t/date-time 2011 8 8 14 0 0)
                    :end_time (t/date-time 2011 8 8 15 0 0)}}
                 (->> task-1 :times (apply hash-set))))
          (is task-2)
          (is (= [{:start_time (t/date-time 2011 8 8 13 0 0)
                   :end_time (t/date-time 2011 8 8 14 0 0)}]
                 (:times task-2))))))))

(deftest removes-conflicting-records-where-overwritten-exactly
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)
          task2-id (-> (create-task! handler task-info-2) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (record-time! handler task2-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))]
        (is (= 1 (count tasks)))
        (let [task-2 (first (filter #(= (:description %) (:description task-info-2)) tasks))]
          (is task-2)
          (is (= [{:start_time (t/date-time 2011 8 8 12 0 0)
                   :end_time (t/date-time 2011 8 8 13 0 0)}]
                 (:times task-2))))))))

(deftest removes-conflicting-records-where-dominated
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)
          task2-id (-> (create-task! handler task-info-2) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (record-time! handler task2-id user (t/date-time 2011 8 8 11 00 00) (t/date-time 2011 8 8 14 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))]
        (is (= 1 (count tasks)))
        (let [task-2 (first (filter #(= (:description %) (:description task-info-2)) tasks))]
          (is task-2)
          (is (= [{:start_time (t/date-time 2011 8 8 11 0 0)
                   :end_time (t/date-time 2011 8 8 14 0 0)}]
                 (:times task-2))))))))

(deftest splits-conflicting-time-records-when-adding-a-sub-interval
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)
          task2-id (-> (create-task! handler task-info-2) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 15 00 00))
      (record-time! handler task2-id user (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))]
        (is (= 2 (count tasks)))
        (let [task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))
              task-2 (first (filter #(= (:description %) (:description task-info-2)) tasks))]
          (is task-1)
          (is (= #{{:start_time (t/date-time 2011 8 8 12 0 0)
                    :end_time (t/date-time 2011 8 8 13 0 0)}
                   {:start_time (t/date-time 2011 8 8 14 0 0)
                    :end_time (t/date-time 2011 8 8 15 0 0)}}
                 (->> task-1 :times (apply hash-set))))
          (is task-2)
          (is (= [{:start_time (t/date-time 2011 8 8 13 0 0)
                   :end_time (t/date-time 2011 8 8 14 0 0)}]
                 (:times task-2))))))))

(deftest insert-shortens-conficting-when-new-time-overlaps-start
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)
          task2-id (-> (create-task! handler task-info-2) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 30 00))
      (record-time! handler task2-id user (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))]
        (clojure.pprint/pprint tasks)
        (is (= 2 (count tasks)))
        (let [task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))
              task-2 (first (filter #(= (:description %) (:description task-info-2)) tasks))]
          (is task-1)
          (is (= [{:start_time (t/date-time 2011 8 8 12 0 0)
                   :end_time (t/date-time 2011 8 8 13 0 0)}]
                 (:times task-1)))
          (is task-2)
          (is (= [{:start_time (t/date-time 2011 8 8 13 0 0)
                   :end_time (t/date-time 2011 8 8 14 0 0)}]
                 (:times task-2))))))))

(deftest insert-shortens-conficting-when-new-time-overlaps-end
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)
          task2-id (-> (create-task! handler task-info-2) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 13 30 00) (t/date-time 2011 8 8 15 00 00))
      (record-time! handler task2-id user (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))]
        (is (= 2 (count tasks)))
        (let [task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))
              task-2 (first (filter #(= (:description %) (:description task-info-2)) tasks))]
          (is task-1)
          (is (= [{:start_time (t/date-time 2011 8 8 14 0 0)
                   :end_time (t/date-time 2011 8 8 15 0 0)}]
                 (:times task-1)))
          (is task-2)
          (is (= [{:start_time (t/date-time 2011 8 8 13 0 0)
                   :end_time (t/date-time 2011 8 8 14 0 0)}]
                 (:times task-2))))))))

(deftest moving-time-between-tasks
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)
          task2-id (-> (create-task! handler task-info-2) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))
      (record-time! handler task1-id user (t/date-time 2011 8 9 13 00 00) (t/date-time 2011 8 9 14 00 00))
      (move-time-on-day! handler task1-id user (t/local-date 2011 8 8) task-info-3)
      (let [tasks (list-time handler user (t/local-date 2011 8 8))]
        (is (empty? (filter #(= (:description %) (:description task-info-1)) tasks)))
        (let [task (first (filter #(= (:description %) (:description task-info-3)) tasks))]
          (is task)
          (is (= [{:start_time (t/date-time 2011 8 8 13 0 0)
                   :end_time (t/date-time 2011 8 8 14 0 0)}]
                 (:times task))))))))

(deftest does-not-modify-non-conflicting-records
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (delete-time! handler user (t/date-time 2011 8 8 14 00 00) (t/date-time 2011 8 8 15 00 00))
      (delete-time! handler user (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))
            task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))]
        (is (= [{:start_time (t/date-time 2011 8 8 12 0 0)
                 :end_time (t/date-time 2011 8 8 13 0 0)}]
               (:times task-1)))))))

(deftest should-not-modify-conflicting-time-records-that-belong-to-other-users
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (delete-time! handler other-user (t/date-time 2011 8 8 14 00 00) (t/date-time 2011 8 8 15 00 00))
      (delete-time! handler other-user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))
            task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))]
        (is (= [{:start_time (t/date-time 2011 8 8 12 0 0)
                 :end_time (t/date-time 2011 8 8 13 0 0)}]
               (:times task-1)))))))

(deftest should-remove-conflicting-time-records-when-idle-time-exactly-overwrites
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (delete-time! handler user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))
            task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))]
        (is (empty? (:times task-1)))))))

(deftest should-remove-conflicting-time-records-when-idle-time-exactly-overwrites
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (delete-time! handler user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))
            task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))]
        (is (empty? (:times task-1)))))))

(deftest delete-removes-conflicting-records-where-dominated
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 00 00))
      (delete-time! handler user (t/date-time 2011 8 8 11 00 00) (t/date-time 2011 8 8 14 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))
            task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))]
        (is (empty? (:times task-1)))))))

(deftest delete-splits-conflicting-time-records-when-adding-a-sub-interval
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)
          task2-id (-> (create-task! handler task-info-2) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 15 00 00))
      (delete-time! handler user (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))]
        (let [task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))
              task-2 (first (filter #(= (:description %) (:description task-info-2)) tasks))]
          (is task-1)
          (is (= #{{:start_time (t/date-time 2011 8 8 12 0 0)
                    :end_time (t/date-time 2011 8 8 13 0 0)}
                   {:start_time (t/date-time 2011 8 8 14 0 0)
                    :end_time (t/date-time 2011 8 8 15 0 0)}}
                 (->> task-1 :times (apply hash-set))))
          (is (empty? (:times task-2))))))))

(deftest delete-shortens-conficting-when-new-time-overlaps-start
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)
          task2-id (-> (create-task! handler task-info-2) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 12 00 00) (t/date-time 2011 8 8 13 30 00))
      (delete-time! handler user (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))]
        #_(clojure.pprint/pprint tasks)
        (let [task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))
              task-2 (first (filter #(= (:description %) (:description task-info-2)) tasks))]
          (is task-1)
          (is (= [{:start_time (t/date-time 2011 8 8 12 0 0)
                   :end_time (t/date-time 2011 8 8 13 0 0)}]
                 (:times task-1)))
          (is (empty? (:times task-2))))))))

(deftest delete-shortens-conficting-when-new-time-overlaps-end
  (with-system
    (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)
          task2-id (-> (create-task! handler task-info-2) get-location task-url-to-id)]
      (empty-table! system :task_time)
      (record-time! handler task1-id user (t/date-time 2011 8 8 13 30 00) (t/date-time 2011 8 8 15 00 00))
      (delete-time! handler user (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))
      (let [tasks (list-time handler user (t/local-date 2011 8 8))]
        (let [task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))
              task-2 (first (filter #(= (:description %) (:description task-info-2)) tasks))]
          (is task-1)
          (is (= [{:start_time (t/date-time 2011 8 8 14 0 0)
                   :end_time (t/date-time 2011 8 8 15 0 0)}]
                 (:times task-1)))
          (is (empty? (:times task-2))))))))

(defn record-some-time [handler]
  (let [task1-id (-> (create-task! handler task-info-1) get-location task-url-to-id)
        task2-id (-> (create-task! handler task-info-2) get-location task-url-to-id)]
    (record-time! handler task1-id user (t/date-time 2011 8 8 13 00 00) (t/date-time 2011 8 8 14 00 00))
    (record-time! handler task1-id user (t/date-time 2011 8 1 13 00 00) (t/date-time 2011 8 1 14 00 00))
    (record-time! handler task2-id user (t/date-time 2011 8 1 14 00 00) (t/date-time 2011 8 2 00 00 00))))

(deftest retrieve-should-include-time-that-starts-or-ends-on-the-query-boundary
  (with-system
    (record-some-time handler)
    (let [tasks (list-time handler user (t/local-date 2011 8 1))]
      #_(clojure.pprint/pprint tasks)
      (let [task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))
            task-2 (first (filter #(= (:description %) (:description task-info-2)) tasks))]
        (is (= [{:start_time (t/date-time 2011 8 1 13 0 0)
                 :end_time (t/date-time 2011 8 1 14 0 0)}]
               (:times task-1)))
        (is (= [{:start_time (t/date-time 2011 8 1 14 0 0)
                 :end_time (t/date-time 2011 8 2 00 0 0)}]
               (:times task-2)))))))

(deftest retrieve-should-include-empty-tasks-with-recent-time-when-they-are-within-recent_hours-of-the-start-time
  (with-system
    (record-some-time handler)
    (let [tasks (list-time handler user (t/local-date 2011 8 15) (t/hours 168))]
      #_(clojure.pprint/pprint tasks)
      (let [task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))]
        (is task-1)
        (is (empty? (:times task-1)))))))

(deftest retrieve-should-mix-recent-tasks-with-current-tasks
  (with-system
    (record-some-time handler)
    (let [tasks (list-time handler user (t/local-date 2011 8 8) (t/hours 168))]
      #_(clojure.pprint/pprint tasks)
      (let [task-1 (first (filter #(= (:description %) (:description task-info-1)) tasks))
            task-2 (first (filter #(= (:description %) (:description task-info-2)) tasks))]
        (is (= [{:start_time (t/date-time 2011 8 8 13 0 0)
                 :end_time (t/date-time 2011 8 8 14 0 0)}]
               (:times task-1)))
        (is task-2)
        (is (empty? (:times task-2)))))))
