(ns time-tracker.graphs
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async
             :refer [<! >! chan close! sliding-buffer put! alts!]]
            [cljs-time.core :as t]
            [cljs-time.internal.core :as tinternal]
            [cljs-time.format :as tf]
            [ajax.core :as ajax]
            [kioo.reagent :refer [content set-attr add-class append do-> substitute listen]]
            [time-tracker.page-layout :refer [layout]]
            [time-tracker.date-picker :refer [date-picker]]
            [time-tracker.date-time :as dt]
            [time-tracker.server :as server]
            [time-tracker.uri-state :as uri])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]
                   [cljs.core.match.macros :refer [match]]
                   [kioo.reagent :refer [defsnippet deftemplate]]))

(enable-console-print!)

(defn update-state! [component f & rest-args]
  (let [current (reagent/state component)
        new-state (apply f current rest-args)]
    ;; This is to allow convergence.
    (when (not= current new-state)
      (reagent/replace-state component new-state))))

(defn spinner []
  [:p "Waiting..."])

(def +width+ 640)
(def +height+ 480)

(defn hsl [h s l]
  (str "hsl(" h ", " s "%, " l "%)"))

(defn linear-scale [src-start src-end target-start target-end]
  {:src-start src-start :src-end src-end
   :target-start target-start :target-end target-end})

(defn scale-by [{:keys [src-start src-end target-start target-end]} pos]
  (let [src-size (- src-end src-start)
        target-size (- target-end target-start)
        ;; Scale from 0 to 1
        canon (-> pos
                  (- src-start)
                  (/ src-size))]
    (-> canon (* target-size) (+ target-start))))

(def +font-height+ 20)
(def +margin+ 20)

(defn render-day
  [{:keys [left periods width hours-scale project-colours bar-label on-click]}]
  (let [ranges (partition 2 1 (reductions + 0 (map :hours periods)))
        periods (map (fn [period [start end]] (assoc period :start start :end end)) periods ranges)
        padding (/ +margin+ 4)
        left' (+ left padding)
        width' (- width (* 2 padding))]
    [:g {:key (hash periods) :on-click on-click}
     [:rect {:x left :width width :y "0%" :height "100%" :fill "white"}]
     (for [{:keys [hours start project start end] :as period} periods]
       (let [bottom (scale-by hours-scale start)
             top (scale-by hours-scale end)
             height (- bottom top)]
         [:g {:key start}
          [:rect {:class "day-bar" :x left' :y top
                  :width width' :height height :style {:opacity 0.8}
                  :fill (get project-colours project)}]
          [:text {:x left' :y top :dy "1.0em" :style {:text-anchor "start"} :on-click #(prn ::foo)}
           (bar-label period)]]))]))

(defn render-project-bars [{:keys [data height x-scale hours-scale project-colours bar-label on-click]
                            :or {bar-label :project}}]
  [:g {:className "project-bars"}
   (let [x-axis (sort (keys data))]
     (for [[i k] (map-indexed vector x-axis)]
       (let [nitems (count x-axis)
             left (scale-by x-scale i)
             width (scale-by x-scale 1)
             periods (get data k)
             on-click (when on-click (partial on-click k))]
         [render-day {:key (hash k) :height height :left left :width width :nitems nitems
                      :hours-scale hours-scale :periods periods :on-click on-click
                      :project-colours project-colours :bar-label bar-label}])))])

(defn- date-label-expanded [date]
  [(dt/date-str date)
   (dt/day-of-week date)])

(defn date-label-compact [start date]
  (let [month-or-day (if (or (= date start) (= 1 (.getDate date)))
                       (tinternal/abbreviate 3 (tf/months (.getMonth date)))
                       (.getDate date))
        dow (tinternal/abbreviate 1 (dt/day-of-week date))]
    [month-or-day dow]))

(defn render-x-labels [{:keys [transform data x-scale date-label on-click] :as props}]
  [:g {:className "x-labels" :transform transform}
   (let [x-labels (sort (keys data))
         nitems (count x-labels)
         width (/ +width+ nitems)]
     (map-indexed
      (fn [idx label]
        (let [midpoint (scale-by x-scale (+ idx 0.5))
              on-click (when on-click (partial on-click label))]
          [:g {:on-click on-click :key (str idx "-" label)}
           (map-indexed
            (fn [lineoff text]
              [:text {:key (str idx "-" lineoff) :x midpoint
                      :y "1em" :dy (str lineoff "em")
                      :style {:text-anchor "middle"}} text])
            (date-label label))]))
      x-labels))])

(defn render-y-axis [{:keys [transform data y-scale] :as props}]
  (let [{:keys [src-start src-end]} y-scale]
    [:g {:transform transform :className "y axis"}
     (for [tick (range src-start (inc src-end))]
       (list
        [:line {:x1 10 :x2 18 :y1 (scale-by y-scale tick) :y2 (scale-by y-scale tick) :stroke "black"}]
        [:text {:x 0 :y (scale-by y-scale tick) :dy "0.3em"} (str tick)]))]))

(defn translate [x y]
  (str "translate(" x "," y ")"))

(defn colour-by-index [items]
  (into (sorted-map)
        (map-indexed
         (fn [i c]
           (let [hue (* 360 (/ i (count items)))]
             [c (hsl hue 100 50)]))
         items)))

(defn obj->map [obj]
  (array-reduce (js-keys obj)
                #(assoc %1 (keyword %2) (aget obj %2))
                {}))

(defn get-component-bbox [component]
  (-> component
      (reagent/dom-node)
      (.getBoundingClientRect)
      obj->map))

(defn- record-size [component]
  (let [{:keys [name on-bounding-box]} (reagent/props component)]
    (on-bounding-box (get-component-bbox component))))

(def +label-radius+ 5)

(def label
  (reagent/create-class
   {:render
    (fn [component]
      (let [{:keys [name colour]} (reagent/props component)]
        [:g
         [:circle {:r +label-radius+ :style {:stroke-width 2, :fill colour}}]
         [:text {:dy "0.33em" :dx (* 2 +label-radius+)} name]]))

    :component-did-mount record-size
    :component-did-update record-size}))

(def render-series-labels
  (reagent/create-class
   {:get-initial-state
    (fn [component]
      {:boxes {}})
    :render
    (fn [component]
      (let [{:keys [project-colours]} (reagent/props component)
            {:keys [boxes]} (reagent/state component)
            projects (reverse (keys project-colours))
            widths (map
                    #(-> % boxes :width ((fnil + 0) (/ +margin+ 2)))
                    projects)
            offsets (zipmap projects (next (reductions - +width+ widths)))]
        [:g {:transform (translate 0 +margin+)}
         (map-indexed
          (fn [i [name colour]]
            [:g {:key i :transform (translate (offsets name +margin+) 0)}
             [label
              {:name name :colour colour
               :on-bounding-box #(update-state! component assoc-in [:boxes name] %)}]])
          project-colours)]))}))

(defn hours-by-project [data]
  (->> data
       vals
       (mapcat identity)
       (group-by :project)
       (map (fn [[k vs]] [k (reduce + (map :hours vs))]))
       (into (sorted-map))))

(defn summary-table [{:keys [data project-colours]}]
  (let [by-project (hours-by-project data)
        total (reduce + (vals by-project))]
    [:table
     [:tbody
      [:tr
       [:th "Project"]
       [:td "Hours tracked"]]
      (for [[project hours] by-project]
        (let [colour (project-colours project)]
          [:tr
           [:td
            [:svg {:height "1em" :width "1em"} [:circle {:r 5 :cx "50%" :cy "50%" :style {:stroke-width 2, :fill colour}}]]
            project]
           [:td hours]]))
      [:tr
       [:td "Total"]
       [:td total]]]]))

(def +round-to-hours+ 4)

(defn- pct [n]
  (str n "%"))

(defn- tween-by [factor zero one]
  (+ (* factor one)
     (* (- 1.0 factor) zero)))

(defn- animation-frame []
  (let [c (chan)]
    (js/window.requestAnimationFrame #(do (put! c %) (close! c)))
    c))

(def +duration-ms+ 100)

(defn- ease [n]
  (-> n
      (* js/Math.PI)
      js/Math.cos
      -
      (+ 1)
      (/ 2)))

(defn run-tween [component duration from to]
  (go
    (let [start (<! (animation-frame))]
      (loop [now start]
        (let [factor (-> now (- start) (/ duration) (min 1.0))]
          (reagent/set-state component
                             {:tween (tween-by (ease factor) from to)})
          (when (< factor 1.0)
            (recur (<! (animation-frame)))))))))

(def project-bar
  (reagent/create-class
   {:get-initial-state
    (fn [component]
      {:tween 0.0})
    :component-did-mount
    (fn [component]
      (let [rodent (chan)]
        (reagent/set-state component {:rodent rodent})
        (go-loop []
          (when-let [msg (<! rodent)]
            (condp = msg
              :entered (<! (run-tween component +duration-ms+ 0 1))
              :left (<! (run-tween component +duration-ms+ 1 0)))
            (recur)))))

    :component-will-unmount
    (fn [component]
      (let [{:keys [rodent]} (reagent/state component)]
        (close! rodent)))

    :render
    (fn [component]
      (let [{:keys [project-colours scale height bar]} (reagent/props component)
            {:keys [tween rodent]} (reagent/state component)
            {:keys [name start length]} bar
            quarter-height (-> height (* (/ 1.0 4)))
            y (tween-by tween quarter-height 0)
            height (tween-by tween quarter-height (* quarter-height 3))
            mouse-in #(put! rodent :entered)
            mouse-out #(put! rodent :left)]
        [:g {:class "project-bar"}
         [:rect {:key (hash name) :y (pct y) :height (pct height)
                 :x (pct (scale-by scale start)) :width (pct (scale-by scale length))
                 :fill (project-colours name)
                 :on-mouse-enter mouse-in :on-mouse-leave mouse-out
                 :style {:opacity (tween-by tween 1 0.5)}}]
         [:text {:y "1.3em" :x (pct (scale-by scale (+ start (/ length 2))))
                 :on-mouse-enter mouse-in :on-mouse-leave mouse-out
                 :style {:text-anchor "middle" :pointer-events "none"
                         :opacity (tween-by tween 0 1)}}
          name]]))}))

(defn summary-bullets [{:keys [data]}]
  (let [height 100
        bar-height (-> height (* 2) (/ 3))
        {:keys [current-week prev-week]} data
        this-week (->> current-week vals (sort-by :name))
        prev-hours (:hours prev-week)
        hours (->> this-week (map :hours))
        running-total (reductions + 0 hours)
        bars (map
              (fn [k start length] {:name k :start start :length length})
              (map :name this-week) running-total hours)
        range-max (-> running-total last (max prev-hours) float (/ +round-to-hours+) (js/Math.ceil) (* +round-to-hours+))
        range-max (if (zero? range-max) +round-to-hours+ range-max)
        scale (linear-scale 0 range-max 0 100)
        project-colours (->> this-week (map :name) (into (sorted-set)) colour-by-index)]
    [:div {:width "100%" :height "3em"}
     [:svg {:width "100%" :height "3em"}
      [:g {:key :background}
       [:rect {:y "0%" :height (-> (/ 3.0 4) (*  bar-height) pct)
               :x "0%" :width "100%" :fill (hsl 0 0 90)}]]
      [:g {:key :scale-markers}
       (for [tick (range 0 (inc range-max) +round-to-hours+)]
         (let [xpos (pct (scale-by scale tick))
               text-anchor (condp = tick 0 "start" range-max "end" "middle")]
           [:g {:key tick}
            [:line {:x1 xpos :x2 xpos :y1 (-> bar-height (* (/ 3.0 4)) pct)
                    :y2 (-> bar-height (* (/ 4.0 4)) pct)
                    :style {:stroke-width 1 :stroke "black"}}]
            [:text {:dy "100%" :x xpos :style {:text-anchor text-anchor}} (str tick)]]))]
      [:g {:key :projects}
       (for [bar bars]
         [project-bar {:key (hash bar) :project-colours project-colours :scale scale :height bar-height :bar bar}])]
      [:g {:key :prev-week-marker}
       (let [xpos (pct (scale-by scale prev-hours))]
         [:line {:x1 xpos :x2 xpos
                 :y1 (-> bar-height (* (/ 1.0 8)) pct) :y2 (-> bar-height (* (/ 5.0 8)) pct)
                 :style {:stroke (hsl 0 0 0) :stroke-width 2.5} :data-hours prev-hours}])]]]))

(defn- record-size-into-state [component]
  (update-state! component assoc :dims (get-component-bbox component)))

(defn projects-chart [{:keys [data project-colours]}]
  (let [bottom-margin-lines 3 top-margin-lines 1
        x-scale (linear-scale 0 (count data) 0 (- +width+ (* 2 +margin+)))
        hours-per-day (map #(reduce + (map :hours %)) (vals data))
        max-hours (js/Math.ceil (apply max (cons 1 hours-per-day)))
        hours-scale (linear-scale 0 max-hours
                                  (- +height+ (* (+ top-margin-lines bottom-margin-lines) +margin+)) 0)]
    [:div {:height +height+ :width +width+}
     [:svg {:height +height+ :width +width+}
      [:g {:transform (translate +margin+ (* (inc top-margin-lines) +margin+))}
       [render-project-bars
        {:data data :height +height+ :x-scale x-scale :hours-scale hours-scale
         :project-colours project-colours}]
       [render-x-labels {:data data :x-scale x-scale
                         :transform (translate 0 (- +height+ (* (+ top-margin-lines bottom-margin-lines) +margin+)))
                         :date-label date-label-expanded}]
       [:g {:transform (translate (- +margin+) 0)}
        [render-y-axis {:data data :y-scale hours-scale}]]]
      [:g {:class "series-labels"}
       [render-series-labels {:project-colours project-colours}]]]]))

(def hours-by-day-chart
  (reagent/create-class
   {:get-initial-state
    (fn projects-chart:get-initial-state [component]
      {:dims nil})
    :render
    (fn [component]
      (let [{:keys [data on-date-selected]} (reagent/props component)
            {:keys [dims]} (reagent/state component)
            {:keys [width height]} dims
            bottom-margin-lines 2 top-margin-lines 0
            x-scale (linear-scale 0 (count data) 0 width)
            hours-per-day (map #(reduce + (map :hours %)) (vals data))
            max-hours (js/Math.ceil (apply max 1 hours-per-day))
            hours-scale (linear-scale 0 max-hours
                                      (- height (* (+ top-margin-lines bottom-margin-lines) +margin+)) 0)
            project-colours (->> data vals (apply concat) (map :project) (into (sorted-set)) colour-by-index)
            handle-click (fn [date event]
                           (when on-date-selected (on-date-selected date)))
            start-date (reduce min (keys data))]

        [:div {:height "4em" :width "100%"}
         [:svg {:height "4em" :width "100%"}
          (when dims
            [:g
             [render-project-bars
              {:data data :height height :width width :x-scale x-scale
               :hours-scale hours-scale :project-colours project-colours
               :bar-label (constantly "") :on-click handle-click}]
             [render-x-labels {:data data :x-scale x-scale
                               :transform (translate 0 (- height (* (+ top-margin-lines bottom-margin-lines) +margin+)))
                               :date-label (partial date-label-compact start-date) :on-click handle-click}]])]]))
    :component-did-mount
    (fn [component]
      (record-size-into-state component)

      (let [listener (fn [_] (record-size-into-state component))]
        (js/window.addEventListener "resize" listener)
        (update-state! component assoc :listener)))

    :component-did-update record-size-into-state
    :component-will-unmount
    (fn [component]
      (let [{:keys [listener]} (reagent/state component)]
        (when listener
          (js/window.removeEventListener "resize" listener)
          (update-state! component dissoc :listener))))}))

(defn render-projects-by-day [{:keys [data project-colours]}]
  (let [project-colours (->> data vals (apply concat) (map :project) (into (sorted-set)) colour-by-index)]
    [:div
     [projects-chart {:data data :project-colours project-colours}]
     [summary-table {:data data :project-colours project-colours}]]))

(defn- fetch-data [component {:keys [query-params url parse] :or {parse identity}}]
  (ajax/GET url {:params query-params
                 :response-format :json :keywords? true
                 :handler #(let [data (parse %)]
                             (reagent/set-state component {:data data}))}))

(def with-data
  (reagent/create-class
   {:component-will-mount
    (fn [component]
      (fetch-data component (reagent/props component)))

    :component-will-receive-props
    (fn [component [_ next-props]]
      (let [cur (reagent/props component)]
        (when-not (= (:query-params cur) (:query-params next-props))
          (fetch-data component next-props))))

    :render
    (fn [component]
      (let [{:keys [user start-date end-date child child-props]}
            (reagent/props component)
            {:keys [data]} (reagent/state component)]
        (if data
          [child (assoc child-props :data data)]
          [spinner])))}))

(defn projects-by-day [{:keys [user start-date end-date] :as props}]
  [with-data
   (assoc props :child render-projects-by-day
          :url "graphs/projects-by-day"
          :query-params {:user-name user :start-date start-date :end-date end-date}
          :parse #(zipmap (map (comp dt/parse-date name) (keys %)) (vals %)))])

(defn projects-bullet-chart [{:keys [date user]}]
  [with-data {:child summary-bullets
              :url "graphs/projects-for-week"
              :query-params {:user-name user :date (dt/to-basic-date date)}}])

(defn hours-per-day-chart [{:keys [date days-back user on-date-selected]}]
  (let [start-date (dt/to-basic-date (dt/day-add date (- days-back)))
        end-date (dt/to-basic-date date)]
    [with-data {:child hours-by-day-chart
                :url "graphs/hours-by-day"
                :child-props {:on-date-selected on-date-selected}
                :query-params {:user-name user :start-date start-date :end-date end-date}
                :parse #(zipmap (map (comp dt/parse-date name) (keys %)) (vals %))}]))
