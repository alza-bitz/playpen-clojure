(ns stats.logistic
  (:require
   [clojure.math :as math]
   [fastmath.ml.regression :as reg]
   [fitdistr.core :as fd]
   [fitdistr.distributions :as fdd]
   [scicloj.ml.tribuo]
   [scicloj.tableplot.v1.plotly :as plotly]
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]))

;; The purpose of this notebook is to understand the [logistic function](https://en.wikipedia.org/wiki/Logistic_function), 
;; [logistic distributions](https://en.wikipedia.org/wiki/Logistic_distribution) and 
;; [logistic regression](https://en.wikipedia.org/wiki/Logistic_regression)
;; including fitting distributions to (sampled) data and measuring goodness-of-fit.

;; Understanding these concepts is useful for gaining confidence in any choice of features for subsequent ml training and testing against a given dataset. 

;; Instead of real data, a synthetic dataset randomly sampled from a logistic distribution is used. 
;; The goal is to understand the theory and process, rather than any specific real-world application.

;; ## 1. Create the distribution and inspect it

;; Define the distribution
(def distribution (fd/distribution :logistic {:mu 0 :s 1}))

;; Inspect a random sample of values from the distribution
(fd/->seq distribution 10)

;; Plot an estimate of the probability density function
(-> {:x (fd/->seq distribution 10000)}
    tc/dataset
    (plotly/layer-density {:=x :x}))

(fd/pdf distribution 0)

(fd/cdf distribution 0)

(fd/probability distribution 0)

;; ## 2. Create a dataset from the distribution and inspect it

;; Create a dataset from a random sample
(def sample-ds (-> {:x (fd/->seq distribution 100)}
                   tc/dataset
                   (tc/map-columns :y
                                   [:x]
                                   (partial fd/cdf distribution))))
;; Note 1. This dataset is based on a random sample of values from the distribution, 
;; adding a `y` column for the corresponding cdf probabilities.

;; Plot it
(plotly/layer-point sample-ds)

;; Define a function that can add noise to values,
;; with optional upper and lower bounds for the case where the values are probabilities
(defn- add-noise [v nmax & {:keys [lb ub]}]
  (let [noise (- (rand (* 2 nmax)) nmax)]
    (cond->> (+ v noise)
      lb (max lb)
      ub (min ub))))

;; Add noise to the `y` values
(def sample-ds-with-noise
  (-> sample-ds
      (tc/map-columns :y-noisy
                      [:y]
                      #(add-noise % 0.3 {:lb 0.01 :ub 0.99}))))
;; Note 1. Noise is added to the probabilities because otherwise at step 5. we get 
;; the error `invalid variance of mean` when training an ML model with `(reg/glm)`.
;; This error occurs because without any added noise, the sampled values from
;; the distribution are "perfectly separated", as in

;; All `x < 0` get `y = 0`

;; All `x ≥ 0` get `y = 1`

;; Plot it to see the effect of the (bounded) noise
(plotly/layer-point sample-ds-with-noise {:=y :y-noisy})

;; Bin the `y-noisy` values to `0` or `1` using `threshold=0.5`. 
;; This is comparable to rounding with `HALF_UP`
(def sample-ds-with-noise-binned
  (-> sample-ds-with-noise
      (tc/map-columns :y-noisy-binned
                      [:y-noisy]
                      #(if (< % 0.5) 0 1))))

;; Inspect the data
sample-ds-with-noise-binned

;; Get some basic stats for the data
(-> sample-ds-with-noise-binned
    (tc/select-columns [:x])
    (tc/info))

;; Plot the data points only.
(plotly/layer-point sample-ds-with-noise-binned {:=y :y-noisy-binned})

;; ## 3. Checking goodness of fit visually (using tribuo)

;; Plot an estimate of the cumulative distribution function, including the line of best fit.
(-> sample-ds-with-noise-binned
    (plotly/layer-point {:=name "actual"
                         :=y :y-noisy-binned})
    (plotly/layer-smooth {:=name "predicted"
                          :=model-options {:model-type :scicloj.ml.tribuo/regression
                                           :tribuo-components [{:name "cart"
                                                                :type "org.tribuo.regression.rtree.CARTRegressionTrainer"}]
                                           :tribuo-trainer-name "cart"}}))
;; Note 1. The api doc for `plotly/layer-smooth` states

;; _"One can also provide the regression model details through `:=model-options`
;; and use any regression model and parameters registered by Metamorph.ml."_

;; For quite some time I didn't realise this statement was actually referring to the 
;; [Tribuo reference](https://scicloj.github.io/noj/noj_book.tribuo_reference.html) and so
;; instead I resorted to tweaking the [example I found in the Tableplot docs](https://scicloj.github.io/tableplot/tableplot_book.plotly_reference.html#layer-smooth) by trial and error.
;; Under examples, see "Custom regression defined by :=model-options:"
;; Linking these docs together would have saved me a lot of time in figuring out how to show the line of best fit..

;; ## 4. Check goodness of fit numerically (using fastmath)

^:kindly/hide-code
(comment
  (-> fdd/distribution-data
      methods
      keys
      sort))

;; Firstly, a comparatively good fit is expected when fitting the data using the `:logistic` method, 
;; because the data is sampled directly from a logistic distribution.
(fd/fit :mle :logistic (fd/->seq distribution 1000))
;; Unfortunately the api for for `fd/fit` doesn't mention the structure of the returned map,
;; but the Noj book [fitting distributions](https://scicloj.github.io/noj/noj_book.distribution_fitting.html#fitting-distributions) chapter states 

;; _"The fitted distribution contains goodness-of-fit statistics, the estimated parameter values, and the distribution name."_

;; Based on that, I deduce the meaning of the map keys as

;; `stats` - goodness-of-fit statistics

;; `params` - the estimated parameter values for the distribution function

;; `distribution-name` - the distribution name

;; Unfortunately, I couldn't find any references in the api docs or Noj book for 
;; the "goodness-of-fit statistics" `:mle` `:aic` and `:bic`. 
;; Although `:mle` is documented as the input method "log likelihood".

;; Secondly, a comparatively worse fit is expected when fitting the data using a method that
;; doesn't match the data, such as `:normal`
(fd/fit :mle :normal (fd/->seq distribution 1000))
;; Note 1. When trying to fit the logistic data to a normal distribution, we get a comparatively 
;; worse fit than before (the value of `:mle` is lower, but only slightly).

;; Thirdly, when trying to fit with a completely non-matching distribution,
;; an error is returned instead of computing a value
(comment 
  (fd/fit :mle :binomial (fd/->seq distribution 1000)))
;;     Assert failed: Data values do not fit required distribution

;; Side quest: experiment to see if the regression works with inverted values
(-> sample-ds-with-noise-binned
    (tc/map-columns :y-noisy-binned-inv [:y-noisy-binned] #(if (= 0 %) 1 0))
    (plotly/layer-point {:=name "actual"
                         :=y :y-noisy-binned-inv})
    (plotly/layer-smooth {:=name "predicted"
                          :=model-options {:model-type :scicloj.ml.tribuo/regression
                                           :tribuo-components [{:name "cart"
                                                                :type "org.tribuo.regression.rtree.CARTRegressionTrainer"}]
                                           :tribuo-trainer-name "cart"}}))
;; Hmm ok, it doesn't seem to be clever enough to flip the regression.

;; ## 5. Training and testing for ML (using fastmath.ml)

;; Split into train and test sets
(def sample-split
  (-> sample-ds-with-noise-binned
      (tc/split :holdout
                {:split-names [:train :test]})
      (tc/group-by :$split-name {:result-type :as-map})))

;; Create a logistic model using the training data.
(def model
  (reg/glm
   (-> sample-split :train :y-noisy-binned)
   (-> sample-split :train (tc/select-columns :x) (tc/rows))
   {:family :binomial
    :names ["x"]
    :tol 0.5}))
;; Note 1. Regarding the api doc for `reg/glm` `ys` and `xss` args.

;; `ys`. The api doc states _"response vector"_ but I am giving it a column

;; `xss`. The api doc states _"terms of systematic component"_. I have no idea what that means, but in practical terms it needs to be a vector of rows 
;; where each row is itself a vector containing values only for the predictor columns

;; Note 2. Regarding the api doc for `reg/glm` options, there is no description for `:family`. 
;; It wasn't obvious to me at all that it is asking about the distribution of `y` (the target) 
;; rather than the distribution of `x` (the predictor).

(type model)

;; Printing the model gives a tabular summary. 
;; Capture the output and display it using Kindly for cleaner formatting.
(comment
  (kind/code
   (with-out-str
     (println
      model))))
;; Note 1. The above code is commented because it fails:

;;      Execution error (NullPointerException) at fastmath.ml.regression/coefficients-table (regression.clj:1289).
;;      Cannot invoke "java.lang.Number.doubleValue()" because "x" is null

;; Now the model can be used to make predictions for the value of `y` based on values of `x`
(model [0.0])
(model [5.0])
(model [-5.0])

^:kindly/hide-code
(comment
  (.setScale (bigdec (model [5])) 2 java.math.RoundingMode/FLOOR))

;; Inspect and compare dataset `y` values with model prediction `y` values
;; by calculating the "model prediction was correct" rates, in both cases `y=0` and `y=1`
(-> sample-split
    :test
    (tc/add-column :y-model-pred
                   (fn [ds] (-> ds
                                (tc/select-columns [:x])
                                (tc/rows :as-vecs)
                                (#(map model %)))))
    (tc/add-column :y-model-pred-rounded
                   (fn [ds] (-> ds
                                :y-model-pred
                                tcc/round)))
    (tc/order-by :y-noisy)
    (tc/group-by [:y-model-pred-rounded])
    (tc/aggregate {:model-pred-correct-rate-y-1 (fn [ds]
                                                  (/ (tc/row-count (tc/select-rows ds (comp #(= 1 %) :y-noisy-binned))) (tc/row-count ds)))
                   :model-pred-correct-rate-y-0 (fn [ds]
                                                  (/ (tc/row-count (tc/select-rows ds (comp #(= 0 %) :y-noisy-binned))) (tc/row-count ds)))})
    (tc/order-by :y-model-pred-rounded))
;; Note 1. The `:model-pred-correct-rate-y-0` value is only valid for `:y-model-pred-rounded` = `0`

;; Note 2. The `:model-pred-correct-rate-y-1` value is only valid for `:y-model-pred-rounded` = `1`

;; ## 6. Training and testing for ML (using metamorph.ml and Tribuo)

;; TODO

