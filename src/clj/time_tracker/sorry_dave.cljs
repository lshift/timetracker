(ns time-tracker.sorry-dave
  (:require [reagent.core :as reagent :refer [atom]]
            [kioo.reagent :refer [content set-attr do-> substitute listen]]
            [time-tracker.page-layout :refer [layout]])
  (:require-macros [kioo.reagent :refer [defsnippet deftemplate]]))

(defsnippet render-sorry-dave "time_tracker/sorry-dave.html" [:.not-found-page] [])

(defn sorry-dave []
  [layout {:header-menus nil}
   [render-sorry-dave]])

