(ns time-tracker.ui-widgets
  (:require [kioo.reagent :refer [content set-attr do-> substitute listen]]
            [reagent.core :as reagent]
            [goog.ui.ac :as ac]
            [goog.events :as gevents]))

(defn uncontrolled-input [value on-changed]
  (do->
   (set-attr :defaultValue value)
   (listen :on-change on-changed)))

(deftype SelectedOptionItem [name data]
  Object
  (toString [_] name))

;; This is designed for those embarassing moments when you've forgotten the
;; names of all of the current projects we're engaged on. So if you search
;; for the empty string (hit space) then you'll be given a list of everything.
(deftype HelpfulReminderMatcher [delegate all-candidates]
  Object
  (requestMatchingRows
    [_ token max-matches match-handler fullstring]
    (if (zero? (.-length token))
      (match-handler token all-candidates)
      (.requestMatchingRows
       delegate token max-matches match-handler fullstring))))

(defn- mount-autocompleter
  ([this]
   (let [{:keys [options]} (reagent/props this)]
     (mount-autocompleter this options)))
  ([this options]
   (let [{:keys [name on-change]} (reagent/props this)]
     (let [dom-node (reagent/dom-node this)
           js-names (apply array (for [[k v] options]
                                   (SelectedOptionItem. v k)))
           auto (ac/createSimpleAutoComplete js-names dom-node false true)
           events (.. ac/AutoComplete -EventType)]

       (.setMatcher auto
                    (HelpfulReminderMatcher.
                     (.getMatcher auto)
                     js-names))

       (gevents/listen auto (. events -UPDATE)
                       (fn autocompleter:on-update [e]
                         (when-let [row (. e -row)]
                           (let [key (. row -data)
                                 name (.-name row)]
                             (reagent/set-state this {:current-key key :current-name name})
                             (.dismiss auto)
                             (on-change key)))))
       (reagent/set-state this {:autocompleter auto})))))

(defn- unmount-autocompleter [this]
  (when-let [auto (:autocompleter (reagent/state this))]
    (.dispose auto)
    (reagent/set-state this {:autocompleter nil})))

(def select-dropdown
  (reagent/create-class
   {:render
    (fn select-dropdown:render [this]
      (let [{:keys [name placeholder on-change value options] :as props}
            (reagent/props this)
            {:keys [current-key current-name]} (reagent/state this)
            inverted (zipmap (vals options) (keys options))]
        [:span
         [:input {:name name :type "text"
                  :default-value (get options value)
                  :value (or current-name (get options value))
                  :placeholder placeholder
                  :class-name (if (nil? current-key) "invalid" "valid")
                  :on-change #(let [v (.. % -target -value)
                                    k (inverted v)]
                                (reagent/set-state this {:current-key k :current-name v})
                                (when k
                                  (-> this reagent/state :autocompleter (.dismiss))
                                  (on-change k)))
                  :on-focus #(let [ac (-> this reagent/state :autocompleter)
                                   renderer (.getRenderer ac)
                                   dom-elt (. % -target)
                                   value (. dom-elt -value)]
                               (.setTarget ac dom-elt)
                               (.setToken ac value)
                               (.redraw renderer))}]
         [:input {:class "id-hack-for-webdriver" :type "hidden" :value (str current-key)}]]))

    :component-did-mount
    (fn select-dropdown:component-did-mount [this]
      (let [{:keys [value]} (reagent/props this)]
        (mount-autocompleter this)
        (reagent/set-state this {:current-key value})))

    :component-will-receive-props
    (fn [this [_ {:keys [name placeholder on-change value options] :as new-props}]]
      (unmount-autocompleter this)
      (mount-autocompleter this options))

    :component-will-unmount
    (fn  select-dropdown:component-will-unmount [this]
      (unmount-autocompleter this))}))
