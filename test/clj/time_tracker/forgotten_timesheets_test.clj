(ns time-tracker.forgotten-timesheets-test
  (:require  [clojure.test :refer [deftest testing is]]
             [ring.mock.request :refer [request]]
             [hickory.core :as hc]
             [hickory.select :as hs]
             [time-tracker.test-systems :refer [with-system ok?]]))

(deftest test-forgotten-sheets-works
  (with-system
    (testing "users listing"
      (let [response (handler (request :get "/forgotten-timesheets"))]
        (is (ok? response))
        (let [html (-> response :body hc/parse hc/as-hickory)]
          (is (= (-> (hs/select (hs/child (hs/tag :h1)) html) first :content first) "Tardy timesheets by person"))
          (is
           (every?
            (set
             (map
              (comp first :content)
              (hs/select (hs/child (hs/and (hs/tag :td) (hs/class "user-name"))) html)))
            ["TomP" "Stuart" "Rolo" "Matthew" "Mikep"]
            ; list of random active users from resources/db/migration/V20110809095043__seed-data.sql who should not be deactivated by tests...
)))))))
