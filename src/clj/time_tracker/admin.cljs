(ns time-tracker.admin
  (:require
   [reagent.core :as reagent]
   [ajax.core :as ajax]
   [clojure.walk :refer [keywordize-keys]]
   [time-tracker.page-layout :refer [layout]]
   [time-tracker.uri-state :as uri]))

; Strip out :tracker_url as project doesn't let you update that yet
(defn keys-to-hash [raw]
  (hash-map (get raw "id")
            (dissoc (keywordize-keys raw) :tracker_url)))

(defn get-handler [state key data]
  (let [remapped (->> data
                      (map keys-to-hash)
                      (apply merge))]
    (swap! state assoc key remapped)))

(defn get-data [state key url default & {:keys [refresh] :or {refresh false}}]
  (if (and (not refresh) (contains? @state key))
    (get @state key)
    (do
      (ajax/GET url {:handler (partial get-handler state key)})
      default)))

(defn put-handler [state key api data]
  (js/console.log "Response" (str key) (pr-str data))
  (get-data state key (str api "?type=all") [] :refresh true))

(defn delete-handler [state key api data]
  (js/console.log "Response" (str key) (pr-str data))
  (get-data state key (str api "?type=all") [] :refresh true))

(defn create-handler [state key api data]
  (js/console.log "Response" (str key) (pr-str data))
  (swap! state assoc :new-name "")
  (get-data state key (str api "?type=all") [] :refresh true))

(defn error-handler [data]
  (let [info (-> data :response keywordize-keys)]
    (if-let [error (:error info)]
      (js/alert error)
      (js/alert info))))

(defn change-field [state api kind id key value]
  (let [existing (get (kind @state) id)
        url (str api "/" id)]
    (ajax/PUT url {:format :json
                   :params (assoc existing key value)
                   :handler (partial put-handler state kind api)
                   :error-handler error-handler})))

(defn delete-entry [state api kind id]
  (let [url (str api "/" id)]
    (ajax/DELETE url {:format :json
                      :handler (partial delete-handler state kind api)
                      :error-handler error-handler})))

; Workaround for https://github.com/JulianBirch/cljs-ajax/issues/129

(defn empty-means-nil [response]
  (if (not= "" (ajax.protocols/-body response))
    response
    (reduced [(-> response ajax.protocols/-status ajax.core/success? boolean) nil])))

(def treat-nil-as-empty
  (ajax/to-interceptor {:name "JSON special case nil"
                        :response empty-means-nil}))

(defn create-entry [state api kind name]
  (ajax/POST api {:format :json
                  :params {:name name}
                  :handler (partial create-handler state kind api)
                  :error-handler error-handler
                  :interceptors [treat-nil-as-empty]}))

; Because we use "name" in the keys later on...
(def named name)

(defn items-table [state kind config is-active]
  (let [active-id (:id @state)
        active-filter #(= (:active %) is-active)
        {:keys [api singular plural]} config
        path (str api "?type=all")]
    [:table {:width "100%"}
     [:tbody
      [:tr
       [:th {:style {:text-align :left}} "Name"]
       [:th {:width "25%"} ""]
       [:th {:width "8%"} ""]
       [:th {:width "7%"} ""]]
      (doall
       (for
        [{:keys [id name active]}
         (sort-by
          (juxt :active #(.toLowerCase (:name %)))
          (-> (get-data state kind path []) vals ((partial filter active-filter))))
         :let [editing (= active-id id)]]
         ^{:key id}
         [:tr {:id id}
          [:td {:class (str (if is-active "active-" "inactive-") (named kind))}
           (if editing
             [:input
              {:class "editor"
               :style {:width "100%"}
               :type :text
               :value (:name @state)
               :on-change #(swap! state assoc :name (-> % .-target .-value))
               :on-key-press
               (fn [e]
                 (if (= 13 (.-charCode e))
                   (do
                     (change-field state api kind id :name (:name @state))
                     (swap! state assoc :id nil))))}]
             name)]
          [:td
           [:input
            {:class (if editing "save-button" "edit-button")
             :type :button
             :style {:width "100%"}
             :on-click (fn []
                         (if editing
                           (do
                             (change-field state api kind id :name (:name @state))
                             (swap! state assoc :id nil))
                           (swap! state assoc :id id :name name)))
             :value (if editing "Save" (str "Edit " name))}]]
          [:td
           [:input
            {:class (if active "disable-button" "enable-button")
             :type :button
             :style {:width "100%"}
             :on-click #(change-field state api kind id :active (not active))
             :value (if active "Disable" "Enable")}]]
          [:td
           [:input
            {:class "delete-button"
             :type :button
             :style {:width "100%"}
             :on-click #(delete-entry state api kind id)
             :value "Delete"}]]]))]]))

(def configs
  {:activities
   {:api "/activities"
    :singular "Activity"
    :plural "Activities"}
   :projects
   {:api "/projects"
    :singular "Project"
    :plural "Projects"}
   :users
   {:api "/users"
    :singular "User"
    :plural "Users"}})

(defonce state (reagent/atom {:page :users :id nil}))

(defn admin [config global-state]
  (fn []
    (let
     [{:keys [page]} @state
      {:keys [current-user]} @global-state
      config (get configs page)]
      [layout {:current-user current-user}
       [:div [:h2 {:id "admin-header"} "Admin"]
        (doall (for [key (keys configs) :let [{:keys [api singular plural]} (get configs key)]]
                 ^{:key key}
                 [:span
                  [:a (if (not= key page)
                        {:id (str (.toLowerCase plural) "-link")
                         :href (uri/admin)
                         :on-click #(swap! state assoc :page key)}
                        {})
                   plural] " "]))
        (let [{:keys [api singular plural]} config]
          [:div
           [:h3 {:id "new-header"} (str "New " singular)]
           [:div
            [:label "Name "]
            [:input {:id "new-entry"
                     :type :text
                     :value (:new-name @state)
                     :on-change #(swap! state assoc :new-name (-> % .-target .-value))}] " "
            [:input {:id "create"
                     :type :button
                     :value "Create"
                     :on-click #(create-entry state api page (:new-name @state))}]]
           [:h3 {:id "active-header"} (str "Active " plural)]
           (items-table state page config true)
           [:h3 {:id "inactive-header"} (str "Inactive " plural)]
           (items-table state page config false)])]])))
