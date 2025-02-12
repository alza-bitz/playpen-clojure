(ns core
  (:require
   [net.cgrand.xforms.io :as xio]
   [clojure.java.io :as io]
   [clojure.string :as str]))

;; 2. What are the top 10 words?

(def lines-sample
  (with-open [reader (io/reader "data/wordfreq/shakespeare.txt")]
    (->> (line-seq reader)
         (take 100)
         doall)))

(comment
 (->> lines-sample
      (mapcat #(str/split % #"\s+"))
      (reduce #(assoc %1 %2 (inc (get %1 %2 0))) {})
      (sort-by second >)))

;; with lazy seq transformations
(with-open
 [reader (io/reader "data/wordfreq/shakespeare.txt")]
  (->> (line-seq reader)
       (remove #(= % ""))
       (mapcat #(str/split % #"\s+"))
       (reduce #(assoc %1 %2 (inc (get %1 %2 0))) {})
       (sort-by second >)
       (take 10)))

;; with eduction
(with-open
 [reader (io/reader "data/wordfreq/shakespeare.txt")]
  (->> (line-seq reader)
       (eduction
        (mapcat #(str/split % #"\s+"))
        (remove #(= % "")))
       (reduce #(assoc %1 %2 (inc (get %1 %2 0))) {})
       (sort-by second >)
       (take 10)))

;; with transduce
(with-open [reader (io/reader "data/wordfreq/shakespeare.txt")]
  (transduce (comp (mapcat #(str/split % #"\s+"))
                   (remove #(= % "")))
             (completing #(assoc %1 %2 (inc (get %1 %2 0)))
                         (comp (partial take 10) (partial sort-by second >)))
             {}
             (line-seq reader)))

;; with cgrand xforms
(transduce (comp (mapcat #(str/split % #"\s+"))
                 (remove #(= % "")))
           (completing #(assoc %1 %2 (inc (get %1 %2 0)))
                       (comp (partial take 10) (partial sort-by second >)))
           {}
           (xio/lines-in "data/wordfreq/shakespeare.txt"))