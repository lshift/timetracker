(ns time-tracker.time-service
  (:require
   [com.stuartsierra.component :as component]
   [liberator.core :refer [resource defresource]]
   [liberator.dev :refer [wrap-trace]]
   [liberator.representation :refer [ring-response]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.json :refer [wrap-json-params]]
   [ring.util.response :refer [response header not-found]]
   [compojure.core :refer [routes ANY GET]]
   [compojure.route :as route]
   [clojure.java.jdbc :as j]
   [clojure.java.io :as io]
   [time-tracker.ring :as ring]
   [clojure.data.json :as json]
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clj-time.coerce :as tc]
   [clojure.tools.logging :as log]
   [clojure.walk :refer [keywordize-keys]]
   [time-tracker.sql.activity :as sql.activity]
   [time-tracker.sql.project :as sql.project]
   [time-tracker.sql.user :as sql.user]
   [time-tracker.time-data :as data]
   [time-tracker.build-version :as vers]
   [schema.core :as s]
   [net.cgrand.enlive-html :as html]
   [clojure.string :as string])
  (:import
   [java.sql BatchUpdateException]))

(defn write-joda [self writer]
  (json/write (f/unparse (f/formatters :date-time) self) writer))

(extend-protocol json/JSONWriter
  org.joda.time.DateTime
  (-write [self writer]
    (write-joda self writer))

  org.joda.time.DateMidnight
  (-write [self writer]
    (write-joda self writer)))

(defn handle-exception [{:keys [exception] :as ctx}]
  (log/error exception "Whilst processing request")
  (let [exceptions
        (take-while #(and (-> % nil? not)
                          (not %))
                    (iterate #(and (instance? java.sql.SQLException %)
                                   (.getNextException %)) exception))]
    (doseq [e (rest exceptions)]
      (log/error "Exception: " (.getMessage e)))
    {:error (string/join "\n\n" (map #(.getMessage %) exceptions))}))

(defn invalid-params? [ctx schema]
  (let [params (get-in ctx [:request :params])
        params (dissoc params :id)]
    (if-let [error (s/check schema params)]
      [true {:validation-failure error}]
      false)))

(defn handle-malformed [{:keys [validation-failure]}]
  (pr-str {:validation-failure validation-failure}))

; This is a bit of a crude workaround for Liberator not supporting
; "409 Conflict" on POST commands directly. Instead we push it down the
; PUT route for POSTs. This might be revisitable once
; https://github.com/clojure-liberator/liberator/pull/246 gets merged
(defn plain-list-resource [db kind queries]
  (resource
   :allowed-methods [:post :get]
   :available-media-types ["application/json"]
   :malformed? (fn [ctx]
                 (if (= (-> ctx :request :request-method) :get)
                   (invalid-params? ctx
                                    {(s/optional-key :type) (s/named (s/cond-pre (s/eq "all") (s/eq "active")) "all or active")})
                   (invalid-params? ctx
                                    {(s/required-key :name) s/Str})))
   :exists? true
   :handle-ok (fn [ctx]
                (let [qp (-> ctx :request :query-params keywordize-keys)
                      typ (keyword (or (:type qp) "active"))
                      items ((:list queries) {} {:connection (:conn db)})]
                  (condp = typ
                    :active (filter #(:active %) items)
                    :all items
                    (throw (Exception. "Validation failed, should be all or active")))))
   :handle-exception handle-exception
   :handle-malformed handle-malformed

   ; These next two are the logic that punts it to the PUT route for POSTs
   :post-to-existing? false
   :put-to-existing? (fn [ctx] (not= (-> ctx :request :request-method) :get))

   :conflict? (fn [ctx]
                (let [params (get-in ctx [:request :params])
                      existing ((:exists queries) params {:connection (:conn db)})]
                  (not= 0 (count existing))))
   ; This is effectively "post!"
   :put! (fn [ctx]
           (let [params (get-in ctx [:request :params])
                 result ((:create queries) params {:connection (:conn db)})]
             {:location (format "/%s/%s" kind (:id result))}))))

(defn plain-entry-resource [db kind queries id]
  (resource
   :allowed-methods [:get :put :delete]
   :available-media-types ["application/json"]
   :exists? (fn [ctx]
              (let [items ((:get queries) {:id id} {:connection (:conn db)})]
                (if (not-empty items)
                  {:entry (first items)})))
   ; PUT needs name/active, GET and DELETE don't
   :malformed?
   (fn [ctx]
     (if (not= (-> ctx :request :request-method) :put)
       (invalid-params? ctx {})
       (invalid-params? ctx
                        {(s/required-key :name) s/Str
                         (s/required-key :active) s/Bool})))
   :handle-exception handle-exception
   :handle-malformed handle-malformed
   :new? false
   :put! (fn [ctx]
           ((:update queries) (merge (get-in ctx [:request :params]) {:id id}) {:connection (:conn db)}))
   :delete! (fn [ctx]
              ((:delete queries) {:id id} {:connection (:conn db)}))
   :handle-no-content (fn [ctx]
                        (if (contains? ctx :error)
                          (ring-response {:status 400 :body (json/write-str {:error (:error ctx)})})))
   :handle-ok (fn [ctx]
                (:entry ctx))))

(defn- parse-time [value]
  (f/parse (f/formatters :date-time) value))

(defn- parse-date [value]
  (f/parse (f/formatters :date-time) value))

(defn- form-value-fn [[k v]]
  (let [k (keyword k)
        v (cond
            (#{:start_time :end_time} k) (parse-time v)
            (#{:task_id :project_id :activity_id} k) (Integer/parseInt v)
            :else v)]
    [k v]))

(defn parse-params [context key schema]
  (when (#{:put :post :delete} (get-in context [:request :request-method]))
    (let [params (get-in context [:request :params])]
      (if-let [error (s/check schema params)]
        [true {:validation-failure error}]
        (let [req (->> params (map form-value-fn) (into {}))]
          [false {key req}])))))

(defn is-migrate-request? [{:keys [id]}]
  (not (nil? id)))

(def TaskData
  {:project_id #"^\d+$"
   :activity_id #"^\d+$"
   :bug (s/maybe s/Str)
   :description (s/maybe s/Str)})

(def MoveData
  (merge TaskData
         {:start #"^\d+-\d+-\d+$"
          :end #"^\d+-\d+-\d+$"
          :id #"^\d+$"
          :user s/Str}))

(def DeleteData
  {:start_time s/Str ;; ISO8601
   :end_time s/Str
   :user s/Str})

(def schemas-by-method
  {:post (s/either TaskData MoveData)
   :delete DeleteData})

(defn time-base [db]
  (resource
   :available-media-types ["application/json"]
   :allowed-methods [:post :get :delete]
   :malformed? #(let [schema (schemas-by-method (get-in % [:request :request-method]))]
                  (parse-params % ::data schema))
   :post! (fn time-base:post! [ctx]
            (log/info ::time-base:post! (::data ctx))
            (let [task-info (::data ctx)
                  new-id (data/ensure-task! db task-info)]
              (when (is-migrate-request? task-info)
                (let [{:keys [id user start end]} task-info]
                  (log/info ::time-base:migrate user start end)
                  (data/migrate-time-period! db (Integer/parseInt id) new-id user start end)))
              {:location (format "/time/%d" new-id)}))

   :handle-malformed
   (fn [{:keys [validation-failure]}]
     (pr-str {:validation-failure validation-failure}))
   :handle-ok
   (fn time-base:handle-ok [ctx]
     (let [{:strs [user start end recent_hours]} (get-in ctx [:request :query-params])
           start (parse-date start)
           end (parse-date end)
           recent-hours (Integer/parseInt recent_hours)]
       (let [r (data/list-tasks-between db user start end (t/hours recent-hours))]
         (log/info :list-response r)
         r)))

   :delete!
   (fn time-base:delete! [{:keys [::data] :as ctx}]
     (let [{:keys [user start_time end_time]} data]
       (log/info ::time-base:delete! data)
       (data/mark-time-idle! db user start_time end_time)))
   :handle-exception handle-exception))

(def datetime-re #"^\d{4}-\d\d-\d\dT\d\d\:\d\d\:\d\d.\d\d\d(\+\d\d:\d\d|Z)$")
(def TaskTime
  {:id         #"\d+"
   :user       s/Str
   :start_time datetime-re
   :end_time   datetime-re})

(defn task-resource [db id]
  (resource
   ;; Adding :representation here due to malformed? happening before media type
   ;; negotiation. See https://github.com/clojure-liberator/liberator/issues/94
   :service-available? {:representation {:media-type "application/json"}}
   :allowed-methods [:post]
   :available-media-types ["application/json"]
   :malformed? #(let [[invalid? data] (parse-params % ::data TaskTime)
                      {:keys [start_time end_time]} (::data data)]
                  (cond
                    invalid?
                    [true data]
                    (not (data/active-user? db (-> data ::data :user)))
                    [true {:validation-failure {:user "No such user"}}]
                    (>= (compare start_time end_time) 0)
                    [true {:validation-failure {:start_time "start_time is after end_time"}}]
                    :else [false data]))
   :handle-malformed (fn [{:keys [validation-failure]}]
                       (pr-str {:validation-failure validation-failure}))

   :post! (fn [ctx]
            (let [{:keys [user start_time end_time] :as data} (::data ctx)]
              (log/info ::task-resource:post! (pr-str data))
              (data/record-time! db (Integer/parseInt id) user start_time end_time)))

   :handle-exception handle-exception))

(def +resource-prefix+ "time_tracker/public/")

(html/deftemplate main-tmpl (str +resource-prefix+ "index.html")
  [version]
  [:#version] (html/content version))

(def +ns+ *ns*)

(defn root-template []
  (-> (apply str (main-tmpl @vers/version))
      response
      (header "content-type" "text/html;charset=utf-8")))

(defn app [{:keys [db] :as self}]
  (routes
   (ANY "/projects" []
     (plain-list-resource db "projects" sql.project/queries))
   (ANY "/projects/:id{[0-9]+}" [id]
     (plain-entry-resource db "projects" sql.project/queries (read-string id)))
   (ANY "/activities" []
     (plain-list-resource db "activities" sql.activity/queries))
   (ANY "/activities/:id{[0-9]+}" [id]
     (plain-entry-resource db "activities" sql.activity/queries (read-string id)))
   (ANY "/users" []
     (plain-list-resource db "users" sql.user/queries))
   (ANY "/users/:id{[0-9]+}" [id]
     (plain-entry-resource db "activities" sql.user/queries (read-string id)))
   (ANY "/time" []
     (time-base db))
   (ANY "/time/:id" [id]
     (task-resource db id))
   (GET "/" []
     (root-template))
   (route/resources "/" {:root +resource-prefix+})
   (route/resources "/react" {:root "react/"})
   (not-found "Not found")))

(defrecord TimeService []
  ring/RingRequestHandler
  (request-handler [self]
    (-> self app wrap-keyword-params wrap-json-params wrap-params)))

(defn instance []
  (map->TimeService {}))
