(ns time-tracker.server
  (:require [cljs.reader]
            [clojure.string :refer [replace]]
            [cljs.core.async :as async
             :refer [<! >! chan close! sliding-buffer put! alts!]]
            [ajax.core :as ajax]
            #_[goog.events :as gevents]
            [time-tracker.date-time :as dt])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(defn index-by [field items]
  (into {} (map #(vector (get % field) %) items)))

(def trailing-slashes #"/*$")

(defn relative-to [{:keys [base-url]} url]
  (str (replace base-url trailing-slashes "") url))

(defn location-response-format []
  {:description "Returns the content of the location header"
   :content-type "*/*" ;; Don't care about the content type
   :read (fn [xhrio]
           (let [location (.getResponseHeader xhrio "Location")
                 [_ id] (re-find #"/time/(\d+)" (or location ""))]
             id))})

(defn fetch-time-sheet-for
  [config {:keys [name] :as user} date tz]
  (let [resp-chan (chan)
        start-inst date
        end-inst (->> date dt/day-after)
        url (relative-to config
                         (str "/time?user=" name
                              "&start=" (dt/to-iso8601 start-inst)
                              "&end=" (dt/to-iso8601 end-inst)
                              "&recent_hours=168"))]
    (ajax/GET url
      {:response-format :json :keywords? true
       :handler (fn [resp]
                  (put! resp-chan (vec resp)))
       :error-handler (fn [resp] (prn :error! resp)
                        (put! resp-chan {:state :error :error resp}))})
    resp-chan))

(defn fetch-projects [config]
  (let [url (relative-to config "/projects")
        resp-chan (async/chan)]
    (ajax/GET url {:response-format :json :keywords? true
                   :handler (fn [resp] (put! resp-chan (index-by :id resp)))
                   :error-handler (fn [resp] (put! resp-chan {:state :error :error resp}))})
    resp-chan))

(defn fetch-activities [config]
  (let [url (relative-to config "/activities")
        resp-chan (async/chan)]
    (ajax/GET url {:response-format :json :keywords? true
                   :handler (fn [resp] (put! resp-chan (index-by :id resp)))
                   :error-handler (fn [resp] (put! resp-chan {:state :error :error resp}))})
    resp-chan))

(defn fetch-users [config]
  (let [url (relative-to config "/users")
        resp-chan (async/chan)]
    (ajax/GET url {:response-format :json :keywords? true
                   :handler (fn [resp] (put! resp-chan (index-by :name resp)))
                   :error-handler (fn [resp] (put! resp-chan {:state :error :error resp}))})
    resp-chan))

(defn post-timebar! [config user id from to]
  (let [resp-ch (chan)
        url (relative-to config (str "/time/" id))]
    (ajax/POST url {:format :raw
                    :params {:user (:name user)
                             :start_time (dt/to-iso8601 from)
                             :end_time (dt/to-iso8601 to)}
                    :response-format :raw
                    :keywords? true
                    :handler (fn [resp] (put! resp-ch resp))
                    :error-handler (fn [resp] (put! resp-ch {:state :error :error resp}))})
    resp-ch))

(defn create-task! [config info]
  (let [resp-ch (chan)
        url (relative-to config "/time")]
    (ajax/POST url {:format (ajax/url-request-format)
                    :response-format (location-response-format)
                    :params info
                    :keywords? true
                    :handler (fn [resp] (put! resp-ch resp))
                    :error-handler (fn [resp] (put! resp-ch {:state :error :error resp}))})
    resp-ch))

(defn mark-idle! [config {:keys [name] :as user} from to]
  (let [resp-ch (chan)
        url (relative-to config "/time")]
    (ajax/DELETE url {:format :raw
                      :params {:user name
                               :start_time (dt/to-iso8601 from)
                               :end_time (dt/to-iso8601 to)}
                      :response-format :raw
                      :keywords? true
                      :handler (fn [_resp] (put! resp-ch true))
                      :error-handler (fn [resp] (put! resp-ch {:state :error :error resp}))})
    resp-ch))

(defn update-task-info! [config {:keys [name] :as user} date id info]
  (let [resp-ch (chan)
        url (relative-to config "/time")
        params (-> info
                   (select-keys [:project_id :activity_id :bug :description])
                   (assoc :id id :user name :start (dt/date-str date)
                          :end (dt/date-str (dt/day-after date))))]
    (ajax/POST url {:format :raw
                    :params params
                    :response-format :raw
                    :keywords? true
                    :handler (fn [resp] (put! resp-ch resp))
                    :error-handler (fn [resp] (put! resp-ch {:state :error :error resp}))})
    resp-ch))
