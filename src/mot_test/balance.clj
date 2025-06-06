(ns mot-test.balance
  (:require
   [tablecloth.api :as tc]))

(defn check-balance-fn
  "Returns a function that will check the balance of the target variable."
  [target]
  (fn [ds]
    (let [tc (tc/row-count ds)]
      (-> ds
          (tc/group-by [target])
          (tc/aggregate {:count tc/row-count})
          (tc/map-columns :pc [:count] #(* 100 (/ % tc)))))))

(defn balance-under-fn
  "Returns a function that will balance the target variable using undersampling of the majority class."
  [target class-a class-b]
  (fn [ds]
    (let [groups (-> ds (tc/group-by [target]) (tc/groups->map))
          class-a-group (get groups {:test_result class-a})
          class-b-group (get groups {:test_result class-b})
          [over-group under-group] (if (> (tc/row-count class-a-group) (tc/row-count class-b-group))
                                     [class-a-group class-b-group] [class-b-group class-a-group])
          under-count (tc/row-count under-group)]
      (-> over-group
          (tc/head under-count)
          (tc/concat under-group)
          (tc/random (* 10 under-count))
          (tc/unique-by)))))
