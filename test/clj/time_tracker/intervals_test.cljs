(ns time-tracker.intervals-test
  (:require [cljs.test :refer-macros [deftest is testing] :as t]
            [clojure.test.check :as sc]
            [clojure.test.check.generators :as gen :refer [such-that]]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :as pt :include-macros true]
            [time-tracker.intervals :refer [make-interval interval-has? interval-union interval-overlap?
                                            interval-intersect interval-difference interval-empty?
                                            interval-encloses?]]))

(enable-console-print!)

(defn a-interval [g]
  (gen/fmap (fn [[a b]] (make-interval a b)) (gen/tuple g g)))

(pt/defspec check-interval-always-ordered 250
  (prop/for-all [x gen/int y gen/int]
                (let [{:keys [lower upper]} (make-interval x y)]
                  (<= lower upper))))

(pt/defspec check-interval-includes 250
  (prop/for-all [x gen/int y gen/int n gen/int]
                (= (and (>= x n) (< n y)
                        (interval-has? (make-interval x y) n)))))

(pt/defspec check-union-interval 250
  (prop/for-all [a (a-interval gen/int) b (a-interval gen/int) n gen/int]
                (= (or (interval-has? a n) (interval-has? b n))
                   (boolean (some #(interval-has? % n) (interval-union a b))))))

(pt/defspec check-union-interval-count 250
  (prop/for-all [a (a-interval gen/int) b (a-interval gen/int) n gen/int]
                (get #{1 2} (count (interval-union a b)))))

(pt/defspec check-interval-intersection 250
  (prop/for-all [a (a-interval gen/int) b (a-interval gen/int) n gen/int]
                (= (and (interval-has? a n) (interval-has? b n))
                   (interval-has? (interval-intersect a b) n))))

(pt/defspec check-interval-difference 250
  (prop/for-all [a (a-interval gen/int) b (a-interval gen/int) n gen/int]
                (let [a-has-n (interval-has? a n)
                      not-b-has-n (not (interval-has? b n))
                      diff-contains-n (boolean (some #(interval-has? % n) (interval-difference a b)))]
                  #_(prn (if (= (and a-has-n not-b-has-n) diff-contains-n) :okay :fail) a b n :=> (interval-difference a b))
                  (= (and a-has-n not-b-has-n) diff-contains-n))))

(pt/defspec check-interval-intersection-overlap 250
  (prop/for-all [a (such-that (comp not interval-empty?) (a-interval gen/int))
                 b (such-that (comp not interval-empty?) (a-interval gen/int))]
                #_(prn :okay? (= (interval-empty? (interval-intersect a b)) (not (interval-overlap? a b)))
                       a b :inter (interval-intersect a b) :empty? (interval-empty? (interval-intersect a b)) :overlap? (interval-overlap? a b))
                (= (interval-empty? (interval-intersect a b))
                   (not (interval-overlap? a b)))))

(pt/defspec check-interval-enclosure-union 250
  (prop/for-all [a (a-interval gen/int) b (a-interval gen/int)]
                (let [union (interval-union a b)]
                  #_(prn a b :enclose? (interval-encloses? a b) :union union)
                  (= (interval-encloses? a b) (= union (list a))))))
