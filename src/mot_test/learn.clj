(ns mot-test.learn
  (:require
   [java-time.api :as time]
   [mot-test.analysis :as analysis]
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]
   [tech.v3.dataset :as ds]
   [tech.v3.dataset.categorical :as ds-cat]
   [tech.v3.dataset.modelling :as ds-mod]))

;; # 2. ML Modelling

;; ## Initial thoughts after analysis

;; Target variable: `test_result`

;; Candidate features:
;; 1. `make` top 20 (categorical, nominal)
;; 2. `model` top 300 (categorical, nominal)
;; 3. `test_mileage` log 10 (non-categorical)
;; 4. `first-use-datediff` log 10 of `test_date` - `first_use_date` (days) (non-categorical)
;; 5. `postcode_area` (categorical, nominal)

;; ## Prepare for train/test

(def top-20-makes
  (-> analysis/test-results-cleansed-balanced-2
      (tc/group-by [:make])
      (tc/aggregate {:make-count tc/row-count})
      (tc/order-by :make-count :desc)
      (tc/head 20)
      :make
      vec))

(def top-300-models
  (-> analysis/test-results-cleansed-balanced-2
      (tc/group-by [:make :model])
      (tc/aggregate {:model-count tc/row-count})
      (tc/order-by :model-count :desc)
      (tc/head 300)
      :model
      vec))

(def postcode-areas
  (-> analysis/test-results-cleansed-balanced-2
      (tc/select-columns :postcode_area)
      (tc/unique-by :postcode_area)
      (tc/order-by :postcode_area)
      :postcode_area
      vec))

(def test-results-filtered (-> analysis/test-results-cleansed-balanced-2
                               (tc/select-rows (comp #(contains? (set top-20-makes) %) :make))
                               (tc/select-rows (comp #(contains? (set top-300-models) %) :model))
                               (tc/add-column :test-mileage-log10 (comp tcc/log10 :test_mileage))
                               (tc/map-columns :first-use-datediff
                                               [:test_date :first_use_date]
                                               (fn [test-date first-use-date]
                                                 (when (and test-date first-use-date)
                                                   (time/time-between first-use-date test-date :days))))
                               (tc/select-rows #(< 0 (% :first-use-datediff)))
                               (tc/add-column :first-use-datediff-log10 (comp tcc/log10 :first-use-datediff))))

;; Check for class imbalance
(analysis/check-balance test-results-filtered)

;; ## Convert categorical features to numeric

(def categorical-feature-columns [:make :model :postcode_area])

(def non-categorical-feature-columns [:test-mileage-log10 :first-use-datediff-log10])

(def feature-columns (concat categorical-feature-columns non-categorical-feature-columns))

(def target-column :test_result)

(def categorical-results
  (-> test-results-filtered
      (tc/select-columns (conj feature-columns target-column))
      (tc/drop-missing)
      (ds/categorical->number [:test_result] ["F" "P"] :int32)
      (ds-mod/set-inference-target target-column)))

(def cat-maps [(ds-cat/fit-categorical-map categorical-results :make top-20-makes :int32)
               (ds-cat/fit-categorical-map categorical-results :model top-300-models :int32)
               (ds-cat/fit-categorical-map categorical-results :postcode_area postcode-areas :int32)])

cat-maps

;; After the mappings are applied, we have a numeric dataset, as expected by most models.

(def numeric-results
  (reduce (fn [ds cat-map]
            (ds-cat/transform-categorical-map ds cat-map))
          categorical-results
          cat-maps))

(tc/head numeric-results 20)

(ds/rowvecs
 (tc/head
  numeric-results))

;; Split data into test and train data sets

(def split (-> numeric-results
               (tc/split->seq :holdout)
               first))

(map analysis/check-balance (vals split))

;; ## Train/test using dummy model

(require '[scicloj.metamorph.ml :as ml]
         '[scicloj.metamorph.ml.classification]
         '[scicloj.metamorph.ml.loss :as loss])

(def dummy-model (ml/train (:train split)
                           {:model-type :metamorph.ml/dummy-classifier}))

(def dummy-prediction
  (ml/predict (:test split) dummy-model))

(-> dummy-prediction :test_result frequencies)

(loss/classification-accuracy
 (:test_result (ds-cat/reverse-map-categorical-xforms (:test split)))
 (:test_result (ds-cat/reverse-map-categorical-xforms dummy-prediction)))

;; Dummy model just predicts the majority class, so around 50% is expected since the data is balanced

;; ## Train/test using logistic regression

(require '[scicloj.ml.tribuo])

(def lreg-model (ml/train (:train split)
                          {:model-type :scicloj.ml.tribuo/classification
                           :tribuo-components [{:name "logistic"
                                                :type "org.tribuo.classification.sgd.linear.LogisticRegressionTrainer"
                                                :properties {:loggingInterval "1000000"}}]
                           :tribuo-trainer-name "logistic"}))

(def lreg-prediction
  (ml/predict (:test split) lreg-model))

(-> lreg-prediction :test_result frequencies)

(loss/classification-accuracy
 (:test_result (ds-cat/reverse-map-categorical-xforms (:test split)))
 (:test_result (ds-cat/reverse-map-categorical-xforms lreg-prediction)))

;; 63%.. getting better but still not great

;; Unfortunately this returns nil :(
(ml/explain lreg-model)

;; ## Train/test using random forest

(def rf-model (ml/train (:train split)
                        {:model-type :scicloj.ml.tribuo/classification
                         :tribuo-components [{:name "random-forest"
                                              :type "org.tribuo.classification.dtree.CARTClassificationTrainer"
                                              :properties {:maxDepth "8"}}]
                         :tribuo-trainer-name "random-forest"}))

(def rf-prediction
  (ml/predict (:test split) rf-model))

(-> rf-prediction :test_result frequencies)

(loss/classification-accuracy
 (:test_result (ds-cat/reverse-map-categorical-xforms (:test split)))
 (:test_result (ds-cat/reverse-map-categorical-xforms rf-prediction)))

;; 64%.. a bit better but still not great

;; Unfortunately this returns nil :(
(ml/explain rf-model)

;; ## Train/test using XGBoost

(def xgb-model (ml/train (:train split)
                         {:model-type :scicloj.ml.tribuo/classification
                          :tribuo-components [{:name "xgboost"
                                               :type "org.tribuo.classification.xgboost.XGBoostClassificationTrainer"
                                               :properties {:numTrees "64"}}]
                          :tribuo-trainer-name "xgboost"}))

(def xgb-prediction
  (ml/predict (:test split) xgb-model))

(-> xgb-prediction :test_result frequencies)

(loss/classification-accuracy
 (:test_result (ds-cat/reverse-map-categorical-xforms (:test split)))
 (:test_result (ds-cat/reverse-map-categorical-xforms xgb-prediction)))

;; 66%.. again a bit better but still not great

;; Unfortunately this returns nil :(
(ml/explain xgb-model)
