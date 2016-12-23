(ns time-tracker.time-sheets
  (:require [reagent.core :as reagent :refer [atom]]
            [kioo.reagent :refer [content set-attr add-class append do-> substitute listen]]
            [cljs.reader]
            [cljs.core.async :as async :refer [put! chan >!]]
            [cljs.core.match]
            [ajax.core :as ajax]
            [goog.events]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]
            [time-tracker.date-time :as dt]
            [time-tracker.intervals :as i]
            [time-tracker.date-picker :refer [render-date-controls]]
            [time-tracker.ui-widgets :refer [uncontrolled-input]]
            [time-tracker.time-sheet-model :as model]
            [time-tracker.time-sheet-events :refer [+new-task+] :as events]
            [time-tracker.page-layout :refer [layout]]
            [time-tracker.uri-state :as uri]
            [time-tracker.ui-widgets :as ui :refer [select-dropdown]]
            [time-tracker.event-handling :refer (process-messages)]
            [time-tracker.graphs :as graphs])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]
                   [cljs.core.match.macros :refer [match]]
                   [kioo.reagent :refer [defsnippet deftemplate]]))

(def return-key 13)
(def nbsp "\u00a0")

(deftype StateAtom [component]
  IDeref
  (-deref [this] (reagent/state component))
  ISwap
  (-swap! [a f]          (-reset! a (f @a)))
  (-swap! [a f x]        (-reset! a (f @a  x)))
  (-swap! [a f x y]      (-reset! a (f @a  x y)))
  (-swap! [a f x y more] (-reset! a (apply f @a x y more)))
  IReset
  (-reset! [a new-value]
    (reagent/replace-state component new-value))

  IPrintWithWriter
  (-pr-writer [a w opts]
    (-write w "#<StateAtom ")
    (pr-writer @a w opts)
    (-write w ">")))

; See https://github.com/reagent-project/reagent-cookbook/tree/master/recipes/ReactCSSTransitionGroup
(def css-transition-group
  (reagent/adapt-react-class (-> js/React (aget "addons") (aget "CSSTransitionGroup"))))

(defn pad2 [n]
  (let [s (str n)]
    (if (< (count s) 2)
      (str "0" s)
      s)))

(defn pct [n]
  (str n "%"))

(defn time-to-frac [{:keys [start-time end-time] :as timebar-meta} instant]
  (let [start-bound (.valueOf start-time)
        end-bound (.valueOf end-time)
        point (.valueOf instant)
        res (/ (- point start-bound) (- end-bound start-bound))]
    res))

(defn time-to-pct [timebar-meta instant]
  (* 100 (time-to-frac timebar-meta instant)))

(defn round [n to]
  (* (quot n to) to))

(defn round-to-nearest [n quotient]
  (round (+ n (/ quotient 2.0)) quotient))

(defn time-range [start-time end-time minute-increment]
  (let [incr (if (integer? minute-increment) (t/minutes minute-increment) minute-increment)
        start-time (tc/to-date-time start-time)
        end-time (tc/to-date-time end-time)
        s (take-while #(<= % end-time) (iterate #(t/plus % incr) start-time))]
    s))

(defn iso-str [t]
  (when t
    (.toIsoString t true true)))

(def +tick-interval+ (* 10 1000))

(def render-current-time
  (reagent/create-class
   {:render
    (fn render-current-time:render [component]
      (let [{:keys [timebar-meta]} (reagent/props component)
            {:keys [now]} (reagent/state component)]
        [:text.current-time-label
         {:x (pct (time-to-pct timebar-meta now)) :y (pct 50)
          :style {:text-anchor "middle"}}
         "now"]))
    :get-initial-state (fn [_] {:now (dt/now)})
    :component-will-mount
    (fn render-current-time:component-will-mount [component]
      (let [t (goog.Timer. +tick-interval+)]
        (goog.events/listen t goog.Timer.TICK
                            (fn []
                              (reagent/set-state component {:now (dt/now)})))
        (reagent/set-state component {:timer t})
        (.start t)))
    :component-will-unmount
    (fn render-current-time:component-will-unmount [component]
      (let [{:keys [timer]} (reagent/state component)]
        (.stop timer)))}))

(defn render-time-marks [{:keys [height width start-time end-time tz] :as timebar-meta} indicated-time]
  (let [start (t/plus (tc/to-date-time start-time) (t/hours 1))
        end (t/minus (tc/to-date-time end-time) (t/hours 1))]
    [:svg.time-marks {:style {:width (pct 100) :height (pct 100)}}
     [:g (for [hour (time-range start end (t/hours 2))]
           (let [xoff (time-to-pct timebar-meta hour)
                 lhour  (dt/as-local-time hour tz)]
             [:text.time-label
              {:key lhour :x (pct xoff) :y (pct 100)
               :style {:text-anchor "middle"}}
              (str (pad2 (.getHours lhour)) ":" (pad2 (.getMinutes hour)))]))]
     [:g
      (when indicated-time
        (let [text-anchor (cond (<= indicated-time start) "start" (>= indicated-time end) "end" :else "middle")
              ltime (-> indicated-time tc/to-date-time (dt/as-local-time tz))]
          [:text.time-label {:x (pct (time-to-pct timebar-meta indicated-time)) :y (pct 50)
                             :style {:text-anchor text-anchor}}
           (str (pad2 (.getHours ltime)) ":"
                (pad2 (.getMinutes ltime)))]))]
     [:g.current-time
      [render-current-time {:timebar-meta timebar-meta}]]]))

(defn to-mark-props [timebar-meta marked-time]
  (let [tz (-> timebar-meta :tz)
        local-time (dt/as-local-time marked-time tz)]
    {:xoff (time-to-pct timebar-meta marked-time)
     :hour (.getHours local-time)
     :minute (.getMinutes local-time)}))

(defn a-tick-mark [{:keys [xoff hour minute]}]
  (let [id (str "tick-" hour "-" minute)
        on-the-hour? (= minute 0)
        ;; This is added for the benefit of our webdriver tests.
        time-classname (str "time" "-" hour "-" minute)
        css-class (apply str (interpose " "
                                        ["tick-mark"
                                         (if on-the-hour? "hour-tick" "quarter-tick")
                                         time-classname]))]
    [:line
     {:class-name css-class :key id :stroke "black"
      :x1 (pct xoff) :y1 0
      :x2 (pct xoff) :y2 (if on-the-hour? (pct 100) (pct 50))}]))

(defn bounds-to-tick-points [{:keys [start-time end-time] :as timebar-meta}]
  (->>
   (time-range start-time end-time 15)

    ; Remove duplicate key if the start/end are the same (i.e. entire 24 hours)
   (#(if (= (.toUsTimeString start-time) (.toUsTimeString end-time))
       (butlast %)
       %))

   (map #(to-mark-props timebar-meta %))
   doall))

(defn render-tick-marks [{:keys [bounds]}]
  (reagent/create-class
   {:reagent-render
    (fn [component]
      [:g.tick-marks
       (for [tick-data (bounds-to-tick-points bounds)]
         (a-tick-mark tick-data))])
    :should-component-update
    (fn [this [_ {old-bounds :bounds}] [_ {new-bounds :bounds}]]
      (let [{old-start :start-time old-end :end-time} old-bounds
            {new-start :start-time new-end :end-time} new-bounds]
        (not (and
              (= (.getHours old-start) (.getHours new-start))
              (= (.getMinutes old-start) (.getMinutes new-start))
              (= (.getHours old-end) (.getHours new-end))
              (= (.getMinutes old-end) (.getMinutes new-end))))))}))

(extend-type js/Date
  IHash
  (-hash [d]
    (hash (.valueOf d))))

(defn clamp [low high val]
  (-> val
      (max low)
      (min high)))

(defn render-a-timebar [timebar-meta {:keys [start_time end_time] :as timebar} class-name]
  (let [start_pos (clamp 0 100 (time-to-pct timebar-meta (min start_time end_time)))
        end_pos   (clamp 0 100 (time-to-pct timebar-meta (max start_time end_time)))
        title (str (dt/to-basic-iso8601 start_time) "-" (dt/to-basic-iso8601 end_time))]
    (if (not= start_pos end_pos)
      [:rect {:x (pct start_pos) :y 0
              :width (pct (- end_pos start_pos)) :height (pct 100)
              :title title
              :class-name class-name}]
      [:g])))

(defn render-assigned-times [timebar-meta times selection]
  [:g
   (list
    (for [time times]
      ^{:key (hash time)} [render-a-timebar timebar-meta time "time-bar"])
    (when selection
      ^{:key "selecting"} [render-a-timebar timebar-meta selection "proposed-time-bar"]))])

(defn parse-mouse-event [this e id {:keys [start-time end-time] :as timebar-meta}]
  (let [bounds (-> this reagent/dom-node .getBoundingClientRect)
        etype (keyword (. e -type))
        xoff (/ (- (.-clientX e) (.-left bounds)) (.-width bounds))
        yoff (/ (- (.-clientX e) (.-top bounds)) (.-height bounds))
          ;; This is the inverse of the forumula in time-to-pct above.
        highlight-time (new js/Date
                            (round-to-nearest
                             (+ (.valueOf start-time)
                                (* xoff (- (.valueOf end-time)
                                           (.valueOf start-time))))
                             (* 15 60 1000)))]
    {:type etype :id id :instant highlight-time}))

(defmulti handle-timebar-event
  (fn [timebar-events selecting-state event]
    #_(prn ::event event)
    (let [discriminator (keyword (namespace ::this-ns) (-> event :type name))]
      discriminator)))

(defmethod handle-timebar-event ::mousedown
  [timebar-events selecting-state {:keys [id instant] :as m}]
  (go
    (prn ::handle-timebar-event:mousedown selecting-state m)
    (swap! selecting-state assoc :start_time instant :end_time instant)
    (prn ::handle-timebar-event:mousedown selecting-state m)))

(defmethod handle-timebar-event ::mousemove
  [timebar-events selecting-state {:keys [id instant] :as m}]
  (go
    (prn ::handle-timebar-event:mousemove selecting-state m)
    (let [{:keys [start_time]} @selecting-state]
      (when start_time
        (swap! selecting-state assoc :end_time instant)))))

(defmethod handle-timebar-event ::mouseup
  [timebar-events selecting-state {:keys [id instant] :as m}]
  (go
    (prn ::handle-timebar-event:mouseup selecting-state m)
    (let [{:keys [start_time]} @selecting-state
          [from to] (sort [start_time instant])

          command {:type :time-selected :id id :from from :to to}]
      (prn :command command)
      (>! timebar-events command)
      (reset! selecting-state nil))))

(defmethod handle-timebar-event ::mouseout
  [timebar-events selecting-state state {:keys [id] :as m}]
  (go
    (prn ::handle-timebar-event:mouseout selecting-state m)
    (reset! selecting-state nil)))

(defn timebar-mouse-event
  [this timebar-meta id event-ch e]
  (when (= (. e -button) 0)
    (let [event (parse-mouse-event this e id timebar-meta)]
      (handle-timebar-event event-ch (->StateAtom this) event)
      (.preventDefault e))))

(defn render-timebars [{:keys [width height] :as timebar-meta} id times events-ch]
  (let [this (reagent/current-component)
        state-atom (->StateAtom this)
        mouse-handler (partial timebar-mouse-event this timebar-meta id events-ch)]
    [:svg.time-bars
     [render-tick-marks {:bounds timebar-meta}]
     [render-assigned-times timebar-meta times @state-atom]
    ;; In SVG, the elements are rendered first-to-last, and because events are
    ;; captured by the first visible element, events are handled from last to
    ;; first. Hence, we add a transparent rectangle to collect mouse events.
     [:rect.event-target
      {:x "0" :y "0" :width "100%" :height "100%" :fill-opacity "0"
       :on-mouse-down mouse-handler
       :on-mouse-out #(do (.log js/console "out" (.-target %)) (mouse-handler %))
       :on-mouse-move mouse-handler
       :on-mouse-up mouse-handler}]]))

(defn timebars-view [{:keys [timebar-meta times]}]
  [:svg.time-bars nil
   [render-tick-marks {:bounds timebar-meta}]
   [render-assigned-times timebar-meta times nil]])

(defn set-timeout [f timeout]
  (.setTimeout js/window f timeout))

(defn- id-name-map [m]
  (let [data (into (hash-map) (map (juxt :id :name) (vals m)))]
    (into (sorted-map-by (fn [key1 key2]
                           (compare [(get data key1) key1]
                                    [(get data key2) key2])))
          data)))

(defn add-hours [dt hours]
  (new js/Date (+ (.valueOf dt) (* hours 3600 1000))))

(defn calculate-idle-times [bounds current-date tasks]
  (let [{:keys [start-time end-time]} bounds
        period (i/make-interval start-time end-time)
        intervals (->> tasks
                       vals
                       (mapcat :times)
                       (map (fn calculate-idle-times:extract-times [{:keys [start_time end_time]}]
                              (i/make-interval start_time end_time))))
        idle-periods (reduce
                      (fn calculate-idle-times:subtract-idle [idle-periods worked]
                        (mapcat #(i/interval-difference % worked) idle-periods))
                      (list period)
                      intervals)]
    (map (fn calculate-idle-times:interval-to-times [{:keys [lower upper]}] {:start_time lower :end_time upper}) idle-periods)))

(defn calculate-stats [tasks]
  (let [times (->> tasks (map :times) (filter identity) flatten)
        periods (for [{:keys [start_time end_time] :as t} times]
                  (when (and start_time end_time)
                    (/ (- (.valueOf end_time) (.valueOf start_time)) 1000 3600)))]
    (reduce + 0 (filter identity periods))))

(defsnippet time-recorded-view "time_tracker/main.html" [:.time-recorded]
  [hours minutes]
  {[:.time-recorded] (content (str hours "h" minutes "m"))})

(defn time-recorded [{:keys [tasks]}]
  (let [recorded-hours (calculate-stats tasks)
        hours (int recorded-hours)
        minutes (rem (* recorded-hours 60) 60)]
    (time-recorded-view hours minutes)))

(defsnippet time-sheet-row "time_tracker/main.html" [:.time-sheet :tr.time-sheet-task]
  [{:keys [timebar-meta activities timebar-events toggle-shown]
    {:keys [id project_id activity_id bug description times] :as task} :task
    :as props} label]

  {[:.time-sheet-task]
   (do->
    (listen :on-double-click #(put! timebar-events {:type :edit-requested :id id}))
    (set-attr :id (str id)))
   [:.project] (content label)
   [:.activity] (content (-> activities (get activity_id {:name (str activity_id "?")}) :name))
   [:.time-bars] (content
                  [render-timebars timebar-meta id times timebar-events])
   [:.bug] (content bug)
   [:.description] (content description)})

(defn on-input-changed [events-ch id field]
  (fn on-input-changed:handler [e]
    (let [value (if-let [t (. e -target)] (. t -value) e)]
      (put! events-ch
            {:type :field-changed
             :id id
             :attribute field
             :value value}))))

;; XXX: The setTimeout is here because the event
;; handler for the row captures the event before
;; it gets to the input element. React ostensibly
;; captures events in capture mode, as the events
;; flow from document root element to the leaves.
;; So, yes, this is the moral equivalent of using
;; Thread/sleep in selenium tests.

(defn- commit-handler [timebar-events id]
  #(set-timeout
    (fn render-task-editing:do-commit-edits []
      (let [event {:type :commit-edits! :id id}]
        (prn ::commit-event event)
        (put! timebar-events event)))
    1))

(defsnippet render-task-editing "time_tracker/main.html"
  [:.time-sheet :tr.editable-time-sheet-task]
  [{:keys [timebar-meta projects activities timebar-events]
    {:keys [id project_id activity_id bug description times] :as task} :task
    :as props}]
  {[:.editable-time-sheet-task]
   (do->
    (set-attr :id (if (= id :time-tracker/new-task) "new-task" (str id)))
    (let [handle-commit (commit-handler timebar-events id)]
      (listen :on-key-down #(do (prn (.-which %) return-key) (when (= (.-which %) return-key) (handle-commit))))))
   [:.editable-time-sheet-task :.commit]
   (listen :on-click (commit-handler timebar-events id))
   [:.project]
   (content
    [select-dropdown {:name "project" :placeholder "Project"
                      :on-change (on-input-changed timebar-events id :project_id)
                      :value project_id
                      :options (id-name-map projects)}])

   [:.activity]
   (content
    [select-dropdown {:name "activity" :placeholder "Activity"
                      :on-change (on-input-changed timebar-events id :activity_id)
                      :value activity_id
                      :options (id-name-map activities)}])
   [:.bug :input]
   (uncontrolled-input bug (on-input-changed timebar-events id :bug))
   [:.description :input]
   (uncontrolled-input description (on-input-changed timebar-events id :description))
   [:.actions]
   (if (= id :time-tracker/new-task) (content nbsp) identity)
   [:.time-bars]
   (if (= id :time-tracker/new-task)
     (content [render-timebars timebar-meta id times timebar-events])
     (content nbsp))})

(defmulti handle-editing-event
  (fn [timebar-events loop-state event]
    (prn ::event event)
    (let [discriminator (keyword (namespace ::this-ns) (-> event :type name))]
      (prn ::handle-editing-event:discriminator (type loop-state) discriminator)
      discriminator)))

(defmethod handle-editing-event ::field-changed
  [timebar-events state {:keys [attribute value] :as m}]
  (go
    (>! timebar-events m)
    state))

(defmethod handle-editing-event ::commit-edits!
  [timebar-events state {:keys [id] :as commit}]
  (go
    (>! timebar-events commit)
    state))

;; At this point, we just delegate down to the BIG GIANT LOOP in
;; time-sheet-events, and allow that to handle these events. At some point, we
;; should refactor that too.
(defmethod handle-editing-event ::time-selected
  [timebar-events state {:keys [id] :as m}]
  (prn ::handle-editing-event:time-selected state m)
  (go
    (>! timebar-events m)

    (prn ::reset? (:reset-on-new-task @state))
    (when (:reset-on-new-task @state)
      (swap! state assoc :task +new-task+)
      (swap! state update-in [:epoch] inc))
    state))

;; we use the eoch to force react to re-instantiate the editor component,
;; resetting it's state.
(def task-editing (reagent/create-class {:get-initial-state
                                         (fn task-editing:get-initial-state [this]
                                           (let [{:keys [task timebar-meta]} (reagent/props this)
                                                 {:keys [reset-on-new-task]} timebar-meta]
                                             {:events-ch (chan) :task task :epoch 0 :reset-on-new-task reset-on-new-task}))

                                         :component-will-mount
                                         (fn task-editing:component-will-mount [this]
                                           (prn ::task-editing:component-will-mount)
                                           (let [{:keys [events-ch]} (reagent/state this)
                                                 {:keys [timebar-events]} (reagent/props this)]
                                             (process-messages events-ch (partial handle-editing-event timebar-events)
                                                               {:init-state (->StateAtom this)})))

                                         :component-will-unmount
                                         (fn task-editing:component-will-mount [this]
                                           (prn ::task-editing:component-did-unmount)
                                           (let [{:keys [events-ch]} (reagent/state this)]
                                             (async/close! events-ch)))
                                         :render
                                         (fn task-editing:render [this]
                                           (let [{:keys [events-ch task epoch]} (reagent/state this)]
                                             ^{:key (str "epoch " epoch)} [render-task-editing
                                                                           (assoc (reagent/props this)
                                                                                  :timebar-events events-ch
                                                                                  :task task)]))}))

(defn render-task
  [{:keys [timebar-meta projects activities timebar-events editing? toggle-shown]
    {:keys [id] :as task} :task :as props} & [label]]
  (if (editing? id)
    ^{:key id} [task-editing props label]
    ^{:key id} [time-sheet-row props label]))

(defn- task-sort-key [projects activities]
  (juxt
   (comp :name projects :project_id)
   (comp :name activities :activity_id)
   :bug
   :description
   :id))

(defn move-attr-value [from to]
  (fn [node]
    (let [v (get-in node [:attrs from])]
      (update-in node [:attrs]
                 (if v
                   #(-> % (assoc to v) (dissoc from))
                   identity)))))

(defn task-header-summary [{:keys [tasks timebar-meta] :as props}]
  [timebars-view {:timebar-meta timebar-meta :times (mapcat :times tasks)}])

(defsnippet render-tasks-header "time_tracker/main.html" [:.time-sheet :tr.tasks-header]
  [{:keys [timebar-meta projects activities toggle-shown hidden? tasks] :as props}
   & [label]]
  {[(attr? :colspan)] (move-attr-value :colspan :colSpan)
   [:.label] (substitute label)
   [:.summary]
   (content
    (when hidden?
      [task-header-summary
       (select-keys props [:tasks :timebar-meta])]))})

(defsnippet project-label "time_tracker/main.html" [:.time-sheet :tr.tasks-header :.label]
  [{:keys [timebar-meta projects activities toggle-shown hidden? tasks] :as props}]
  {[:.project-name] (content (-> tasks first :project_id projects :name))
   [:.show-hide] (do->
                  (listen :on-click toggle-shown)
                  (content (if hidden? "+" "-")))})

(defn time-sheet-group [initial]
  (let [state (reagent/atom (assoc initial :hidden? false))]
    (fn [initial]
      (swap! state merge initial)
      (let [{:keys [timebar-meta timebar-events projects activities tasks editing? hidden?] :as props} @state
            pid (-> tasks first :project_id)
            toggle-shown #(swap! state update :hidden? not)
            label [project-label (-> @state
                                     (select-keys [:timebar-meta :projects :activities :tasks])
                                     (assoc :toggle-shown toggle-shown :hidden? hidden?))]]
        [css-transition-group {:key (str pid "-" hidden?) :transitionName "fade"
                               :transitionEnterTimeout 500 :transitionLeaveTimeout 500
                               :component :tbody :className "project-group"}
         (if hidden?
           [render-tasks-header (-> @state
                                    (select-keys [:timebar-meta :projects :activities :tasks])
                                    (assoc :toggle-shown toggle-shown :hidden? hidden?))
            label]
           (for [[index {:keys [id] :as row}] (map vector (range) tasks)]
             ^{:key id} [render-task (-> props
                                         (select-keys [:timebar-meta :projects :activities :timebar-events :editing?])
                                         (assoc :task row))
                         (when (zero? index) label)]))]))))

(defn lshift-logo []
  [:img {:class "branding-logo" :src "logo-blocky.svg"}])

(defn render-time-sheet [root]
  (let [{:keys [projects activities timebar-events tasks
                new-task updating? editing? current-date
                current-user indicated-time timebar-meta]} root
        sorted-tasks (sort-by (task-sort-key projects activities) (vals tasks))]
    [:table.time-sheet.pure-table
     [:tbody
      [:tr [:td {:colSpan 5 :rowSpan 2} [lshift-logo]]
       [:td [render-time-marks timebar-meta indicated-time]]]
      [:tr.idle-time-sheet-task {:id "idle-task"}
       [:td [render-timebars timebar-meta :time-tracker/idle
             (calculate-idle-times timebar-meta current-date tasks)
             timebar-events]]]]

     (for [project-tasks (partition-by :project_id sorted-tasks)
           :let [pid (-> project-tasks first :project_id)]]
       ^{:key pid} [time-sheet-group
                    {:timebar-meta timebar-meta :projects projects
                     :activities activities :timebar-events timebar-events
                     :tasks project-tasks :editing? editing?}])
     [:tbody.project-group.new-time-sheet-tasks
      [task-editing (-> root
                        (select-keys [:timebar-meta :projects :activities :timebar-events])
                        (assoc :task new-task))]
      [:tr
       [:td {:colSpan 5}]
       [:td nil "Total: "
        [time-recorded {:tasks (vals tasks)}]]]]]))

(defn is-working? [{:keys [projects activities updating? tasks]}]
  (not (or updating? (nil? projects)
           (nil? activities) (nil? tasks))))

(defn go-to-date! [current-user date]
  (set! js/document.location.hash
        (uri/time-sheet-dated-path {:user current-user :date (dt/date-str date)})))

(defsnippet render-tracker-page "time_tracker/main.html" [:.main-pane]
  [{:keys [current-user timebar-events current-date tasks] :as root}]
  {[:.main-pane]
   (add-class (if (is-working? root) "timetracker-active" "timetracker-updating"))

   [:.time-sheet] (content (render-time-sheet root))
   [:.spinner] (content (if (is-working? root) nbsp "working"))
   [:.this-week-summary]
   (content
      ;; version is used purely to force refreshes when the data changes.
    [graphs/projects-bullet-chart {:user current-user :date current-date :version (hash (:tasks root))}]
    [graphs/hours-per-day-chart
     {:user current-user :date current-date :days-back 28
      :on-date-selected (partial go-to-date! current-user)}])})

(defn tracker-controls
  [{:keys [root user-name date]}]
  (let [{:keys [timebar-events]} root]
    (render-date-controls timebar-events date
                          #(uri/time-sheet-dated-path {:user user-name :date (dt/date-str %)}))))

(def time-sheet
  (reagent/create-class
   {:component-will-mount
    (fn [component]
      (let [{:keys [user-name date state config]} (reagent/props component)
            timebar (chan)
            commands (chan)
            procs {:controller (events/process-ui-events! timebar commands state)
                   :model (model/model-loop commands state config)}]
        (put! commands {:command :change-user :user-name user-name})
        (put! commands {:command :go-to-date :which date})
        (reagent/set-state component
                           {:comms {:timebar-events timebar :commands commands}
                            :procs procs})))

    :component-will-receive-props
    (fn [component next-argv]
      (let [commands (-> component (reagent/state) :comms :commands)
            {current-date :date current-user :user} (reagent/props component)
            {next-date :date next-user :user} (nth next-argv 1)]
        (when (not= current-date next-date)
          (put! commands {:command :go-to-date :which next-date}))
        (when (not= current-user next-user)
          (put! commands {:command :change-user :user-name next-user}))))

    :component-will-unmount
    (fn [component]
      (let [{:keys [comms]} (reagent/state component)]
        (doseq [ch (vals comms)]
          (async/close! ch))))
    :render
    (fn time-sheet:render [component]
      (let [{:keys [user-name date state]} (reagent/props component)
            {:keys [current-user]} @state
            {:keys [comms]} (reagent/state component)]
        (let [root @state]
          [layout {:header-menus [tracker-controls {:root root :user-name user-name :date date}]
                   :current-user current-user :date date}
           [render-tracker-page (-> root
                                    (assoc :current-user user-name :current-date date)
                                    (merge comms))]])))}))
