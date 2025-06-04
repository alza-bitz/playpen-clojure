(ns mot-test.analysis
  (:require
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]
   [clojure.string :as str]
   [scicloj.tableplot.v1.plotly :as plotly]
   [fastmath.stats :as stats]
   [java-time.api :as time]))

;; # 1. Data Analysis

;; ## Load data
(defonce test-results (tc/dataset "data/test_result_sample.csv"
                                  {:separator "|"
                                   :key-fn keyword
                                   :column-blocklist ["test_class_id" "test_type"]}))
;; Notes.
;; 1. See `processing.clj` for rules used to extract `test_result_sample.csv` from `test_result.csv`
;; 2. Raw data in `test_result.csv` downloaded from https://www.data.gov.uk/dataset/e3939ef8-30c7-4ca8-9c7c-ad9475cc9b2f/anonymised_mot_test
;; 3. Specifically, https://data.dft.gov.uk/anonymised-mot-test/test_data/dft_test_result_2023.zip

;; ## Inspect data

;; How many rows?
(tc/row-count test-results)

;; What are the column names?
(tc/column-names test-results)

;; Some basic stats..
(tc/info test-results)

;; A few columns have missing values
(def test-results-cleansed
  (-> test-results
      (tc/drop-missing [:test_mileage :cylinder_capacity])))

;; ### Test result

;; What are the test result values?
(let [total-count (tc/row-count test-results-cleansed)]
  (-> test-results-cleansed
      (tc/group-by [:test_result])
      (tc/aggregate {:count tc/row-count})
      (tc/map-columns :pc [:count] #(* 100 (/ % total-count)))))
;; Setting expectations.. The model needs to have at least 75% accuracy to be 
;; better than just guessing "P" every time

;; Undersample the majority class
(def test-results-cleansed-balanced
  (let [groups
        (-> test-results-cleansed
            (tc/group-by [:test_result])
            (tc/groups->map))
        passes (get groups {:test_result "P"})
        fails (get groups {:test_result "F"})
        fails-count (tc/row-count fails)]
    (-> passes
        (tc/head fails-count)
        (tc/concat fails)
        (tc/random (* 8 fails-count))
        (tc/unique-by :test_id))))

(-> test-results-cleansed-balanced
    (tc/group-by [:test_result])
    (tc/aggregate tc/row-count))

;; ### Mileage

(-> test-results-cleansed-balanced
    (tc/select-columns [:test_mileage :test_result])
    (tc/random 10000)
    (plotly/layer-boxplot {:=x :test_result
                           :=y :test_mileage}))

(-> test-results-cleansed-balanced
    (tc/select-columns [:test_mileage :test_result])
    (tc/random 10000)
    (tc/add-column :test-mileage-log10 (comp tcc/log10 :test_mileage))
    (plotly/layer-histogram {:=x :test-mileage-log10
                             :=histogram-nbins 30
                             :=color :test_result
                             :=mark-opacity 0.5}))

(def test-mileage-stats
  (stats/stats-map
   (vec (:test_mileage test-results-cleansed))))

^:kindly/hide-code
(comment
  (not (< (:Q1 test-mileage-stats) 1000 (:Q3 test-mileage-stats))))

;; Plot the points
(-> test-results-cleansed-balanced
    (tc/select-columns [:test_mileage :test_result])
    (tc/drop-rows #(not (< (:Q1 test-mileage-stats) (:test_mileage %) (:Q3 test-mileage-stats))))
    (tc/map-columns :test_result_numeric [:test_result] #(if (= "P" %) 1 0))
    (tc/map-columns :test_mileage_inv [:test_mileage] #(* -1 %))
    (tc/random 100)
    (plotly/layer-point {:=x :test_mileage_inv
                         :=y :test_result_numeric}))

(require '[scicloj.ml.tribuo])

;; Plot a fitted line by adding a smooth layer
(-> test-results-cleansed-balanced
    (tc/select-columns [:test_mileage :test_result])
    (tc/drop-rows #(not (< (:Q1 test-mileage-stats) (:test_mileage %) (:Q3 test-mileage-stats))))
    (tc/map-columns :test_result_numeric [:test_result] #(if (= "P" %) 1 0))
    (tc/map-columns :test_mileage_inv [:test_mileage] #(* -1 %))
    (tc/random 100)
    (plotly/base {:=x :test_mileage_inv
                  :=y :test_result_numeric})
    (plotly/layer-point)
    (plotly/layer-smooth {:=model-options {:model-type :scicloj.ml.tribuo/regression
                                           :tribuo-components [{:name "cart"
                                                                :type "org.tribuo.regression.rtree.CARTRegressionTrainer"}]
                                           :tribuo-trainer-name "cart"}}))
;; It doesn't seem to fit a logistic distribution at all!

;; ### Make

;; How many unique make?
(-> test-results-cleansed-balanced
    (tc/unique-by :make)
    tc/row-count)

;; What about empty make?
(-> test-results-cleansed-balanced
    (tc/group-by (comp nil? #(re-matches #"\s*" %) :make)))

;; Find clean make values - most popular makes
(-> test-results-cleansed-balanced
    (tc/group-by :make)
    (tc/aggregate {:make-count tc/row-count})
    (tc/order-by :make-count :desc)
    (tc/map-columns :pc :make-count #(* 100 (/ % (tc/row-count test-results-cleansed-balanced))))
    (tc/add-column :pc-cumsum (comp tcc/cumsum :pc))
    (tc/head 20))
;; Notes.
;; 1. Top 10 makes covers 65% of the data.
;; 2. Top 20 makes covers 90% of the data.
;; 3. Top 50 makes covers 99% of the data.

;; What is the distribution of test pass across top 20 makes?
(-> test-results-cleansed-balanced
    (tc/group-by [:make])
    (tc/aggregate {:make-count tc/row-count
                   :pass-rate (fn [ds] (-> ds
                                           :test_result
                                           (tcc/eq "P")
                                           tcc/mean))})
    (tc/add-column :make-count-log10 (comp tcc/log10 :make-count))
    (tc/order-by :make-count :desc)
    (tc/head 20)
    (plotly/base {:=x :make-count-log10
                  :=y :pass-rate})
    (plotly/layer-point)
    (plotly/layer-text {:=text :make}))

^:kindly/hide-code
(comment
  (let [values (range 1 10)]
    (reduce #(conj %1 (+ (last %1) %2)) [(first values)] (rest values))))

;; ### Model

;; How many unique model?
(-> test-results-cleansed-balanced
    (tc/unique-by :model)
    tc/row-count)

;; What about empty model?
(-> test-results-cleansed-balanced
    (tc/group-by (comp nil? #(re-matches #"\s*" %) :model)))

;; Find clean model values - most popular models
(-> test-results-cleansed-balanced
    (tc/group-by [:make :model])
    (tc/aggregate {:model-count tc/row-count})
    (tc/order-by :model-count :desc)
    (tc/map-columns :pc :model-count #(* 100 (/ % (tc/row-count test-results-cleansed-balanced))))
    (tc/add-column :pc-cumsum (comp tcc/cumsum :pc))
    (tc/head 300))
;; Notes.
;; 1. Top 300 makes covers 90% of the data.

;; ### Difference between first use date and test date

(-> test-results-cleansed-balanced
    (tc/map-columns :first-use-datediff
                    [:test_date :first_use_date]
                    (fn [test-date first-use-date]
                      (time/time-between first-use-date test-date :days)))
    (tc/random 10000)
    (plotly/layer-boxplot {:=x :test_result
                           :=y :first-use-datediff}))

(-> test-results-cleansed-balanced
    (tc/map-columns :first-use-datediff
                    [:test_date :first_use_date]
                    (fn [test-date first-use-date]
                      (when (and test-date first-use-date)
                        (time/time-between first-use-date test-date :days)))) 
    (tc/random 10000)
    (tc/select-rows #(< 0 (% :first-use-datediff)))
    (tc/add-column :first-use-datediff-log10 (comp tcc/log10 :first-use-datediff))
    (plotly/layer-histogram {:=x :first-use-datediff-log10
                             :=histogram-nbins 30
                             :=color :test_result
                             :=mark-opacity 0.5}))

;; ### Cylinder capacity

(-> test-results-cleansed-balanced
    (tc/select-columns [:cylinder_capacity :test_result])
    (tc/random 10000)
    (plotly/layer-boxplot {:=x :test_result
                           :=y :cylinder_capacity}))

;; ### Mileage per year

(-> test-results-cleansed-balanced
    (tc/map-columns :first-use-datediff
                    [:test_date :first_use_date]
                    (fn [test-date first-use-date]
                      (time/time-between first-use-date test-date :days)))
    (tc/map-columns :mileage-per-year [:test_mileage :first-use-datediff] #(/ %1 (/ %2 365.25))) 
    (tc/random 10000)
    (plotly/layer-boxplot {:=x :test_result
                           :=y :mileage-per-year}))

;; ### Postcode area

(-> test-results-cleansed-balanced
    (tc/group-by [:postcode_area])
    (tc/aggregate {:count tc/row-count
                   :pass-rate (fn [ds] (-> ds
                                           :test_result
                                           (tcc/eq "P")
                                           tcc/mean))})
    (tc/order-by :count :desc)
    (tc/map-columns :pc :count #(* 100 (/ % (-> test-results-cleansed-balanced tc/row-count))))
    (tc/add-column :pc-cumsum #(-> % :pc tcc/cumsum))
    (tc/head 100))

;; ### Fuel type

(-> test-results-cleansed-balanced
    (tc/group-by [:fuel_type :test_result])
    (tc/aggregate {:count tc/row-count})
    (tc/pivot->wider :test_result :count {:drop-missing? false})
    (tc/replace-missing ["P" "F"] :value 0)
    (tc/map-columns :pass-rate ["P" "F"] #(/ (or %1 0) (+ (or %1 0) (or %2 0)))))

;; ### Colour

(-> test-results-cleansed-balanced
    (tc/group-by [:colour :test_result])
    (tc/aggregate {:count tc/row-count})
    (tc/pivot->wider :test_result :count {:drop-missing? false})
    (tc/replace-missing ["P" "F"] :value 0)
    (tc/map-columns :pass-rate ["P" "F"] #(/ (or %1 0) (+ (or %1 0) (or %2 0)))))
