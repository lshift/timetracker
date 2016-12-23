(ns time-tracker.intervals)

(defrecord IntervalSet [lower upper])

(defn make-interval [a b]
  (if (< a b)
    (->IntervalSet a b)
    (->IntervalSet b a)))

(defn interval-has? [{:keys [lower upper]} n]
  (and (<= lower n) (< n upper)))

(defn interval-encloses? [{a0 :lower a1 :upper :as a} {b0 :lower b1 :upper :as b}]
  (and
   (interval-has? a b0)
   (interval-has? a b1)))

(defn interval-union [{a0 :lower a1 :upper :as a} {b0 :lower b1 :upper :as b}]
  (cond
    (interval-encloses? a b) (list a)
    (interval-encloses? b a) (list b)
    :else (list a b)))

(defn interval-overlap? [{a0 :lower a1 :upper :as a} {b0 :lower b1 :upper :as b}]
  (or
   (interval-has? a b0)
   (interval-has? b a0)))

(defn interval-intersect [{a0 :lower a1 :upper :as a} {b0 :lower b1 :upper :as b}]
  (if (interval-overlap? a b)
    (make-interval (max a0 b0) (min a1 b1))))

(defn interval-difference [{a0 :lower a1 :upper :as a} {b0 :lower b1 :upper :as b}]
  (cond
    ;; a0    a1
    ;;    b0    b1
    (and (interval-has? a b0) (interval-has? b a1))
    (list (make-interval a0 b0))
    ;; a0       a1
    ;;    b0 b1
    (and (interval-has? a b0) (interval-has? a b1))
    (list (make-interval a0 b0) (make-interval b1 a1))
    ;;    a0    a1
    ;; b0    b1
    (and (interval-has? b a0) (interval-has? a b1))
    (list (make-interval b1 a1))
    ;;    a0 a1
    ;; b0       b1
    (and (interval-has? b a0) (interval-has? b a1))
    nil
    ;;    a0 a1
    ;; b0    b1
    (and (interval-has? b a0) (= a1 b1))
    nil
    ;; a0    a1
    ;;    b0 b1
    (and (interval-has? a b0) (= a1 b1))
    (list (make-interval a0 b0))
    ;; Otherwise, no overlap.
    :else (list a)))

(defn interval-empty? [{:keys [lower upper]}]
  (if (and lower upper)
    (= lower upper)
    true))
