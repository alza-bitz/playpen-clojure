(ns tablecloth
  (:require
   [clojure.string :as str]
   [core :refer [download!]]
   [tablecloth.api :as tc]))

;; 1. Download the dataset http://www.gutenberg.org/files/100/100-0.txt

(download! "http://www.gutenberg.org/files/100/100-0.txt"
           "data/wordfreq/shakespeare.txt")

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