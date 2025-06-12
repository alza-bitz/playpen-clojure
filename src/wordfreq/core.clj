(ns core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hato.client :as hc]
   [net.cgrand.xforms :as x]
   [net.cgrand.xforms.io :as xio]))

(defn download!
  [src dst]
  (with-open [in (:body (hc/get src
                                {:as :stream
                                 :http-client {:redirect-policy :always}}))
              out (-> dst
                      (doto (io/make-parents))
                      io/output-stream)]
    (io/copy in out)))

;; 1. Download the dataset http://www.gutenberg.org/files/100/100-0.txt

(download! "http://www.gutenberg.org/files/100/100-0.txt"
           "data/wordfreq/shakespeare.txt")

;; 2. What are the top 10 words?

;; Experiment with a sample
(comment
  (def lines-sample
    (with-open [reader (io/reader "data/wordfreq/shakespeare.txt")]
      (->> (line-seq reader)
           (take 100)
           doall))))

;; Attempt 1 at reducing function over sample
(comment
  (->> lines-sample
       (mapcat #(str/split % #"\s+"))
       (reduce #(assoc %1 %2 (inc (get %1 %2 0))) {})
       (sort-by second >)
       (take 10)))

;; Attempt 2 at reducing function over sample
(comment
  (->> lines-sample
       (mapcat #(str/split % #"\s+"))
       (reduce #(update %1 %2 (fnil inc 0)) {})
       (sort-by second >)
       (take 10)))

;; with lazy seq transformations and reduce
(with-open
 [reader (io/reader "data/wordfreq/shakespeare.txt")]
  (->> (line-seq reader)
       (remove #(= % ""))
       (mapcat #(str/split % #"\s+"))
       (reduce #(update %1 %2 (fnil inc 0)) {})
       (sort-by second >)
       (take 10)))

;; with lazy seq transformations and frequencies instead of reduce
(with-open
 [reader (io/reader "data/wordfreq/shakespeare.txt")]
  (->> (line-seq reader)
       (remove #(= % ""))
       (mapcat #(str/split % #"\s+"))
       frequencies
       (sort-by second >)
       (take 10)))

;; with eduction
(with-open
 [reader (io/reader "data/wordfreq/shakespeare.txt")]
  (->> (line-seq reader)
       (eduction
        (mapcat #(str/split % #"\s+"))
        (remove #(= % "")))
       frequencies
       (sort-by second >)
       (take 10)))

;; with transduce (can't use frequencies as reducing step function)
(with-open [reader (io/reader "data/wordfreq/shakespeare.txt")]
  (transduce (comp (mapcat #(str/split % #"\s+"))
                   (remove #(= % "")))
             (completing #(update %1 %2 (fnil inc 0))
                         (comp (partial take 10) (partial sort-by second >)))
             {}
             (line-seq reader)))

;; with cgrand xforms lines-in (can't use frequencies as reducing step function)
(transduce (comp (mapcat #(str/split % #"\s+"))
                 (remove #(= % "")))
           (completing #(update %1 %2 (fnil inc 0))
                       (comp (partial take 10) (partial sort-by second >)))
           {}
           (xio/lines-in "data/wordfreq/shakespeare.txt"))

;; with cgrand xforms lines-in and xforms x/by-key
(transduce (comp (mapcat #(str/split % #"\s+"))
                 (remove #(= % ""))
                 (x/by-key identity x/count))
           (completing conj
                       (comp (partial take 10) (partial sort-by second >)))
           {}
           (xio/lines-in "data/wordfreq/shakespeare.txt"))