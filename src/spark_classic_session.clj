(ns spark-classic-session
  (:import
   (org.apache.spark.sql SparkSession)))

;; starting state assumptions: classic jar included and connect jar excluded on classpath

(def session (.. (SparkSession/builder)
                 (master "local")
                 (getOrCreate)))

;; this works for the classic session impl
(.sparkContext session)

;; this works, just old-fashioned local mode spark
(.. session
    (read)
    (option "sep" ":")
    (csv "/etc/passwd")
    (collect))

(.close session)