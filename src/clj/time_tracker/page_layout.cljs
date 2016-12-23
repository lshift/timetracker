(ns time-tracker.page-layout)

(defn layout
  [{:keys [header-menus current-user date]} & main]
  [:div#layout
   [:div#header
    [:div.pure-menu.pure-menu-open.pure-menu-horizontal
     header-menus
     [:a.pure-menu-heading {:title "Timetracker: The Revenge" :href "#/"} "Timetracker"]
     [:ul.nav-menu
      (if current-user
        [:li [:a {:href (str "#/users/" (:name current-user))} "Time sheets"]]
        [:li [:a {:href "#/"} "Pick user"]])
      [:li [:a {:href "#/preferences" :id "preferences-link"} "Preferences"]]
      [:li [:a {:href "#/timezilla" :id "timezilla-link"} "Timezilla"]]
      [:li [:a {:href "#/admin" :id "admin-link"} "Admin"]]]]]
   (apply vector :div.main-pane main)])
