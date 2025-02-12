(ns tablecloth
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hato.client :as hc]
            [tablecloth.api :as tc]))

;; 1. Download the dataset http://www.gutenberg.org/files/100/100-0.txt

(with-open [in (:body (hc/get "http://www.gutenberg.org/files/100/100-0.txt"
                              {:as :stream
                               :http-client {:redirect-policy :always}}))
            out (io/output-stream "data/wordfreq/shakespeare.txt")]
  (io/copy in out))

;; 2. What are the top 10 words?

(def lines (-> (tc/dataset "data/wordfreq/shakespeare.txt"
                           {:header-row? false
                            :separator "\n"
                            :key-fn (fn [_] (identity "line"))})
               (tc/select-rows #(re-find #"\b" (get % "line" "")))))

(tc/row-count lines) ;; 153619

(def words (-> lines
               (tc/add-column
                "word"
                #(map (fn [l] (str/split l #"\s+")) (get % "line")))
               (tc/unroll "word")
               (tc/drop-columns "line")))

(tc/row-count words) ;; 963472

(-> words
    (tc/group-by "word")
    (tc/aggregate {"count" tc/row-count})
    (tc/order-by "count" :desc)
    (tc/head 10))