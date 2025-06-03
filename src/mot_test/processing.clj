(ns mot-test.processing
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [java-time.api :as t]
   [net.cgrand.xforms.io :as xio]))

(defn partition-writer
  [header]
  (fn
    ([] {})
    ([writers] (doseq [[_ writer] writers] (.close writer)) writers)
    ([writers item]
     (let [{:keys [partition line]} item
           writer (or (get writers partition)
                      (-> (str "data/partition_" partition ".csv")
                          io/writer
                          (doto (.write (str header "\n")))))]
       (.write writer (str line "\n"))
       (assoc writers partition writer)))))

(defn pred-test-class-id-4
  [row]
  (= "4" (:test_class_id row)))

(defn pred-test-type-normal
  [row]
  (= "NT" (:test_type row)))

(defn pred-test-result-pass-fail
  [row]
  (or (= "P" (:test_result row)) (= "F" (:test_result row))))

(defn pred-first-day-of-month
  [row]
  ((comp #(= "01" (get % 2)) #(str/split % #"-") :test_date) row))

(comment
  (pred-first-day-of-month {:test_date "2023-01-01"}))

(defn partition-monthly
  [row]
  ((comp #(str "monthly_"  %) second #(str/split % #"-") :test_date) row))

(comment
  (partition-monthly {:test_date "2023-01-01"}))

(defn partition-weekly
  [row]
  ((comp #(format "weekly_%02d" %) t/value #(t/property % :aligned-week-of-year) t/local-date :test_date) row))

(comment
  (partition-weekly {:test_date "2023-01-08"}))

(defn partition-daily
  [row]
  ((comp #(str "daily_"  (get % 1) "_" (get % 2)) #(str/split % #"-") :test_date) row))

(comment
  (partition-daily {:test_date "2023-01-08"}))

(defn ->row
  [keys values]
  (->> values
       (interleave keys)
       (partition 2)
       (map vec)
       (into {})))

(comment
  (->row ["c_1" "c_2"] ["v1" "v2"]))

(def ^:private delim
  "|")

(def ^:private delim-re
  #"\|")

(defn vals-in-keys-order
  [row keys]
  (reduce #(conj %1 (get row %2)) [] keys))

(comment
  (let [header "test_id|vehicle_id|test_date|test_class_id|test_type|test_result|test_mileage|postcode_area|make|model|colour|fuel_type|cylinder_capacity|first_use_date"
        header-keys (map keyword (str/split header delim-re))
        line "1994821045|838565361|2023-01-02|4|NT|P|179357|NW|TOYOTA|PRIUS +|WHITE|HY|1798|2016-06-17"
        row (->row header-keys (str/split line delim-re))]
    [header
     (vals-in-keys-order row header-keys)]))

(comment
  (with-open [reader (io/reader "data/test_result.csv")]
    (let [lines (line-seq reader)
          header (first lines)
          header-keys (map keyword (str/split header delim-re))]
      (transduce
       (comp
        (map #(str/split % delim-re))
        (map (partial ->row header-keys))
        (filter pred-test-class-id-4)
        (filter pred-test-type-normal)
        (filter pred-test-result-pass-fail)
        (map #(into {:partition (partition-monthly %)
                     :line (str/join delim (vals-in-keys-order % header-keys))})))
       ;;  conj 
       (partition-writer header)
       (rest lines)))))

(comment
  (with-open [reader (io/reader "data/test_result.csv")
              writer (io/writer "data/test_result_sample.csv")]
    (let [lines (line-seq reader)
          header (first lines)
          header-keys (map keyword (str/split header delim-re))]
      (transduce
       (comp
        (map #(str/split % delim-re))
        (map (partial ->row header-keys))
        (filter pred-test-class-id-4)
        (filter pred-test-type-normal)
        (filter pred-test-result-pass-fail)
        (filter pred-first-day-of-month)
        (map #(str/join delim (vals-in-keys-order % header-keys))))
       xio/lines-out
      ;;  conj
       (doto writer (.write (str header "\n")))
       (rest lines)))))