(ns time-tracker.users-list
  (:require [reagent.core :as reagent :refer [atom]]
            [kioo.reagent :refer [content set-attr add-class append do-> substitute listen]]
            [cljs.core.async :as async :refer [put!]]
            [time-tracker.server :as server]
            [time-tracker.uri-state :as uri])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]
                   [kioo.reagent :refer [defsnippet deftemplate]]))

(def render-user-list
  (reagent/create-class
   {:component-will-mount
    (fn [component]
      (go
        (let [{:keys [config]} (reagent/props component)
              users (<! (server/fetch-users config))]
          (reagent/set-state component {:users users}))))
    :render
    (fn render-user-list:render [component events-ch]
      (let [{:keys [events-ch] :as props} (reagent/props component)
            {:keys [users] :as state} (reagent/state component)
            sorted-users (sort-by #(-> % :name .toLowerCase) (vals users))]
        [:div.users-page
         [:ul
          (for [{:keys [id name]} sorted-users]
            [:li {:key (str "user-" id)}
             [:a {:href (uri/time-sheet-path {:user name})} name]])]]))}))

