(ns stats.logistic
  (:require
   [fastmath.ml.regression :as reg]
   [fitdistr.core :as fd]
   [scicloj.ml.tribuo]
   [scicloj.tableplot.v1.plotly :as plotly]
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]))

;; The purpose of this notebook is to understand logistic distributions and logistic regression 
;; including goodness-of-fit, to gain confidence in the choice of features for subsequent ml training and testing. 

;; A synthetic dataset randomly sampled from a logistic distribution is used, rather than real data,
;; because the goal is to understand the theory and process rather than any specific application.

^:kindly/hide-code
(comment
  (-> fdd/distribution-data
      methods
      keys
      sort))

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

;; Define a function that can add noise to values,
;; with optional upper and lower bounds for the case where the values are probabilities
(defn- add-noise [nmax v & {:keys [lb ub]}]
  (let [noise (- (rand (* 2 nmax)) nmax)]
    (cond->> (+ v noise)
      lb (max lb)
      ub (min ub))))

;; Create the dataset
(def sample-ds
  (-> {:x (fd/->seq distribution 100)}
      tc/dataset
      (tc/map-columns :y
                      [:x]
                      (fn [x]
                        (let [pr-x (fd/cdf distribution x)]
                          (if (< (add-noise 0.3 pr-x {:lb 0.01 :ub 0.99}) 0.5) 0 1))))))
;; Note 1. This dataset is based on a random sample of values from the distribution, 
;; adding a column for the corresponding cdf probabilities.

;; Note 2. Before assigning y as 0 or 1 based on the threshold (0.5),
;; noise is added to the probabilities, otherwise we get the error 
;; `invalid variance of mean` when calling `(reg/glm)`. This error occurs because 
;; without any added noise, the sampled values from the distribution are "perfectly separated", as in:

;; All `x < 0` get `y = 0`

;; All `x ≥ 0` get `y = 1`

;; Inspect the data
sample-ds

;; Get some basic stats for the data
(-> sample-ds
    (tc/select-columns [:x])
    (tc/info))

;; Plot the data points only.
(plotly/layer-point sample-ds)

;; ## 3. Checking goodness of fit

;; Firstly, check the fit visually.
;; Plot an estimate of the cumulative distribution function, including the line of best fit.
(comment
  (-> logistic-sample-ds
      (plotly/layer-point)
      (plotly/layer-smooth {:=model-options {:regression-model :logistic
                                             :model-type :scicloj.ml.tribuo/regression}})))
;; Note 1. The above code is commented out because I get a `ClassCastException` which prevents Clay from making the file.
;; Full error details:

;;     Execution error (ClassCastException) at com.oracle.labs.mlrg.olcut.config.json.JsonLoader/parseJson (JsonLoader.java:161).
;;     class com.fasterxml.jackson.databind.node.NullNode cannot be cast to class com.fasterxml.jackson.databind.node.ArrayNode (com.fasterxml.jackson.databind.node.NullNode and com.fasterxml.jackson.databind.node.ArrayNode are in unnamed module of loader 'app')

;; Note 2. The api doc for `plotly/layer-smooth` states:
;; _"One can also provide the regression model details through `:=model-options`
;; and use any regression model and parameters registered by Metamorph.ml."_
;; But how can I see what regression models are registered?
;; Otherwise, how does one know what can/should be specified for either `:regression-model` or `:model-type`?

;; Secondly, check the fit numerically.
;; A good fit is expected because the data is sampled directly from a logistic distribution.
(fd/fit :mle :logistic (fd/->seq distribution 1000))
;; I couldn't find any references in the api docs for the stats `:mle` `:aic` and `:bic`.
;; Although, `:mle` is documented as the method "log likelihood".

;; Repeat but with added noise.
;; A comparatively worse fit is expected because of the added noise. 
(fd/fit :mle :logistic (map (partial add-noise 0.2) (fd/->seq distribution 1000)))

;; Repeat again with even more added noise. 
(fd/fit :mle :logistic (map (partial add-noise 1) (fd/->seq distribution 1000)))

;; Repeat again with even more added noise. 
(fd/fit :mle :logistic (map (partial add-noise 10) (fd/->seq distribution 1000)))

;; The fitness stats are changing with added noise, 
;; in particular the "log likelihood" is decreasing but I don't know how to interpret this.

;; At this point I suppose I need to go back to school and increase my knowledge of stats! https://en.wikipedia.org/wiki/Logistic_regression

;; ## 4. Training and testing for ML

;; Split into train and test sets
(def sample-split
  (-> sample-ds
      (tc/split :holdout
                {:split-names [:train :test]})
      (tc/group-by :$split-name {:result-type :as-map})))

;; Create a logistic model using the training data.
(def model
  (reg/glm
   (-> sample-split :train :y)
   (-> sample-split :train (tc/select-columns :x) (tc/rows))
   {:family :binomial
    :tol 0.5}))
;; Note 1. Regarding the api doc for `reg/glm` `ys` and `xss` args.

;; `ys`. The api doc states _"response vector"_ but I am giving it a column

;; `xss`. The api doc states _"terms of systematic component"_. I have no idea what that means, but in practical terms it needs to be a vector of rows 
;; where each row is itself a vector containing values only for the predictor columns

;; Note 2. Regarding the api doc for `reg/glm` options, there is no description for `:family`. 
;; It wasn't obvious to me at all that it is asking about 
;; the distribution of y (the target) rather than the distribution of x (the predictor)

;; Now the model can be used to compute responses based on input values
(model [0.0])
(model [5.0])
(model [-5.0])

^:kindly/hide-code
(comment
  (.setScale (bigdec (model [5])) 2 java.math.RoundingMode/FLOOR))

;; Inspect and compare dataset `y` values with model prediction `y` values
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
    (tc/order-by :y))

;; Extend to find the "model prediction was correct" rates, in both cases `y=0` and `y=1`
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
    (tc/group-by [:y-model-pred-rounded])
    (tc/aggregate {:model-pred-correct-rate-y-1 (fn [ds]
                                                  (/ (tc/row-count (tc/select-rows ds (comp #(= 1 %) :y))) (tc/row-count ds)))
                   :model-pred-correct-rate-y-0 (fn [ds]
                                                  (/ (tc/row-count (tc/select-rows ds (comp #(= 0 %) :y))) (tc/row-count ds)))})
    (tc/order-by :y-model-pred-rounded))


;; ## 5. Some additional details regarding the error `Invalid variance of mean`.

;;     Execution error (ExceptionInfo) at fastmath.ml.regression/glm$fn (regression.clj:1006).
;;     Invalid variance of mean.

;; Note: when I look in `regression.clj:1006` the exception is thrown using `ex-info` with `{:mean g :variance v :coeff idx}` but this is not being printed!
;; However, if I eval `*e` at the repl, the exception data is printed, for example: `{:mean 1.0, :variance 0.0, :coeff 49}`

;; But I don't understand where mean of `1.0` and variance of `0.0` are coming from?

;; It should be noted that a plain Google search for "fastmath invalid variance of mean" produces no useful results.

