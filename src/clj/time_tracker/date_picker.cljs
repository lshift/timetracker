(ns time-tracker.date-picker
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.reader]
            [cljs.core.async :as async :refer [put!]]
            [cljs.core.match]
            [ajax.core :as ajax]
            [goog.ui.ac :as ac]
            [goog.events :as gevents]
            [time-tracker.date-time :as dt]
            [time-tracker.preferences :as prefs]
            [time-tracker.intervals :as i])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]
                   [cljs.core.match.macros :refer [match]]
                   [kioo.reagent :refer [defsnippet deftemplate]])
  (:import [goog.ui DatePicker]))

(defn cleanup-datepicker [component]
  (let [{:keys [datepicker]} (reagent/state component)]
    (.dispose datepicker)
    (reagent/set-state component {:datepicker nil})))

(defn date-changed [component]
  (let [{:keys [datepicker]} (reagent/state component)
        {:keys [on-selected date]} (reagent/props component)
        selected-date (.getDate datepicker)]
    (when (not= date selected-date)
      (on-selected selected-date)
      true)))

(defn create-datepicker [component]
  (let [picker (DatePicker.)
        dom-node (reagent/dom-node component)
        {:keys [on-selected date]} (reagent/props component)]
    (.setDate picker date)

    (gevents/listen picker DatePicker.Events.CHANGE
                    #(when (date-changed component)
                       (cleanup-datepicker component)))

    (.render picker dom-node)
    (reagent/set-state component {:datepicker picker})))

(defn toggle-datepicker [component]
  (if (:datepicker (reagent/state component))
    (cleanup-datepicker component)
    (create-datepicker component)))

(def month-calendar "\uD83D\uDCC5");; 📅 http://www.fileformat.info/info/unicode/char/1f4c5/index.htm
(def leftwards-arrow "&#x2190;") ;; ←
(def rightwards-arrow "&#x2192;") ;; →

(def date-picker
  (reagent/create-class
   {:render
    (fn date-picker:render [component]
      [:span
       [:button {:title "choose date"
                 :on-click #(toggle-datepicker component)}
        (seq (reagent/children component))]])
    :componentDidUpdate
    (fn date-picker:component-did-update [component]
      (let [{:keys [date]} (reagent/props component)
            {:keys [datepicker]} (reagent/state component)]
        (when datepicker
          (.setDate datepicker date))))}))

(defn render-date-controls
  [events-ch the-date link-for-date]
  [:ul#date-controls.right-menu
   [:li [:a.prev-day {:title "Previous Day"
                      :href (link-for-date (dt/day-before the-date))
                      :dangerouslySetInnerHTML {:__html leftwards-arrow}}]]
   [:li [:a.today {:title "Today" :href (link-for-date (dt/today))} "Today"]]
   [:li [:a.next-day {:title "Next Day"
                      :href (link-for-date (dt/day-after the-date))
                      :dangerouslySetInnerHTML {:__html rightwards-arrow}}]]
   [:li [:span.choose-date [date-picker
                            {:date the-date
                             :on-selected #(set! js/document.location.hash (link-for-date %))}
                            month-calendar]]]
   [:li.current-date (dt/prettify-date the-date)]])
