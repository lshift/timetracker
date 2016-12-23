(ns time-tracker.admin-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [clojure.string :refer [lower-case]]
   [environ.core :refer [env]]
   [cemerick.url :refer [url]]
   [clj-webdriver.remote.server :refer [new-remote-session]]
   [time-tracker.test-systems :refer [random-string]]
   [clj-http.client :as client])
  (:use
   [clj-webdriver.taxi])
  (:import
   [org.openqa.selenium NoAlertPresentException NoSuchElementException]))

(defmacro selenium-test [& body]
  `(let [selenium-url# (url (:selenium-url env))
         timetracker-url# (:timetracker-app-url env)
         admin-url# (format "%s#/admin" timetracker-url#)
         [server# driver#] (new-remote-session
                            {:port (get selenium-url# :port 4444)
                             :host (:host selenium-url#)
                             :existing true}
                            {:browser :firefox})]
     (try
       (set-driver! driver#)
       (to admin-url#)
       (wait-until #(exists? {:xpath "//td[@class='active-users']"}))
       ~@body
       (finally
         (quit driver#)))))

(defn find-single-element [query]
  (let [elements (find-elements query)
        amount (count elements)]
    (condp = amount
      1 (first elements)
      0 (throw (NoSuchElementException. (str query)))
      (throw (Exception. (format "Too many elements for %s. Expected 1 and got %d" query amount))))))

(defn find-with-id [id]
  (find-single-element {:id id}))

(defn goto-page [plural]
  (let [lower (lower-case plural)
        link {:id (str lower "-link")}]
    (if (exists? link) ; assume we're already on the right page otherwise
      (do
        (click link)
        (wait-until #(exists? {:xpath (format "//td[@class='active-%s']" lower)}))))))

(defn admin-headers [singular plural]
  (selenium-test
   (goto-page plural)
   (let [header (find-with-id "admin-header")
         new-entry (find-with-id "new-header")
         active (find-with-id "active-header")
         inactive (find-with-id "inactive-header")]
     (is (= "Admin" (text header)))
     (is (= (format "New %s" singular) (text new-entry)))
     (is (= (format "Active %s" plural) (text active)))
     (is (= (format "Inactive %s" plural) (text inactive))))))

(defn match-kind [plural name active]
  {:xpath (format "//td[@class='%sactive-%s' and text()='%s']" (if active "" "in") (lower-case plural) name)})

(defn button-path [plural name active button]
  {:xpath (format "//td[@class='%sactive-%s' and text()='%s']/../td/input[@class='%s-button']" (if active "" "in") (lower-case plural) name button)})

(defn add-new-kind [plural]
  (let [new-entry (find-with-id "new-entry")
        create (find-with-id "create")
        new-id (random-string)]
    (send-keys new-entry new-id)
    (click create)
    (wait-until #(exists? (match-kind plural new-id true)) 3000)
    new-id))

(defn admin-add-new [plural]
  (selenium-test
   (goto-page plural)
   (add-new-kind plural)))

(defn admin-delete [plural]
  (selenium-test
   (goto-page plural)
   (let [new-item (add-new-kind plural)
         delete (find-single-element (button-path plural new-item true "delete"))]
     (is (exists? (match-kind plural new-item true)))
     (click delete)
     (wait-until #(and
                   (not (exists? (match-kind plural new-item true)))
                   (not (exists? (match-kind plural new-item false))))
                 3000))))

(defn get-id-for-active-item [plural name]
  (read-string (attribute {:xpath (format "//td[@class='active-%s' and text()='%s']/.." (lower-case plural) name)} :id)))

(defn make-and-get-id [plural]
  (goto-page plural)
  (let [new-item (add-new-kind plural)]
    [(get-id-for-active-item plural new-item) new-item]))

(defn test-delete-active [plural new-item]
  (goto-page plural)
  (click (find-single-element (button-path plural new-item true "delete")))
  (wait-until #(try
                 (alert-obj)
                 true
                 (catch NoAlertPresentException e
                   false)) 3000)
  (accept)
  (is (exists? (match-kind plural new-item true))))

(deftest admin-delete-active
  (selenium-test
   (let [new-user (add-new-kind "Users")
         [activity-id new-activity] (make-and-get-id "Activities")
         [project-id new-project] (make-and-get-id "Projects")
         app-url (:timetracker-app-url env)
         time-post (client/post
                    (format "%stime" app-url)
                    {:throw-entire-message? true
                     :form-params {:activity_id activity-id
                                   :project_id project-id
                                   :bug ""
                                   :description ""}})]
     (client/post
      (str (url app-url (-> time-post :headers :location)))
      {:throw-entire-message? true
       :form-params {:user new-user
                     :start_time "2016-11-22T04:00:00.000Z"
                     :end_time "2016-11-22T05:00:00.000Z"}})
     (test-delete-active "Users" new-user)
     (test-delete-active "Projects" new-project)
     (test-delete-active "Activities" new-activity))))

(defn admin-disable [plural]
  (selenium-test
   (goto-page plural)
   (let [active-item (add-new-kind plural)
         disable (find-single-element (button-path plural active-item true "disable"))]
     (is (exists? (match-kind plural active-item true)))
     (click disable)
     (wait-until #(exists? (match-kind plural active-item false)) 3000))))

(defn admin-enable [plural]
  (selenium-test
   (goto-page plural)
   (let [inactive-item (text {:xpath (format "//td[@class='inactive-%s']" (lower-case plural))})
         enable (find-single-element (button-path plural inactive-item false "enable"))]
     (is (exists? (match-kind plural inactive-item false)))
     (click enable)
     (wait-until #(exists? (match-kind plural inactive-item true)) 3000))))

(defn admin-edit [plural]
  (selenium-test
   (goto-page plural)
   (let [existing-item (add-new-kind plural)
         edit (find-single-element (button-path plural existing-item true "edit"))
         save-path {:xpath (format "//td[@class='active-%s']/input[@class='editor']/../../td/input[@class='save-button']" (lower-case plural))}
         editor {:xpath "//input[@class='editor']"}
         new-name (random-string)
         new-name-path (match-kind plural new-name true)]
     (click edit)
     (wait-until #(exists? save-path) 3000)
     (clear editor)
     (send-keys editor new-name)
     (click save-path)
     (wait-until #(exists? new-name-path) 3000))))

(defmacro test-admin-group [singular plural]
  (let [lower (lower-case singular)]
    `(do
       (deftest ~(symbol (format "admin-%s-headers" lower))
         (admin-headers ~singular ~plural))
       (deftest ~(symbol (format "admin-%s-add-new" lower))
         (admin-add-new ~plural))
       (deftest ~(symbol (format "admin-%s-delete" lower))
         (admin-delete ~plural))
       (deftest ~(symbol (format "admin-%s-disable" lower))
         (admin-disable ~plural))
       (deftest ~(symbol (format "admin-%s-enable" lower))
         (admin-enable ~plural))
       (deftest ~(symbol (format "admin-%s-edit" lower))
         (admin-edit ~plural)))))

(test-admin-group "Activity" "Activities")
(test-admin-group "Project" "Projects")
(test-admin-group "User" "Users")
