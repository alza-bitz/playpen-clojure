(ns spark-connect-session
  (:import
   (org.apache.spark.sql SparkSession)))

;; starting state assumptions: connect jar included and classic jar excluded on classpath 

(def session (.. (SparkSession/builder)
                 ;; note: this example works even without calling "remote", I suspect because "sc://localhost" is the default value
                 (remote "sc://localhost")
                 ;; note: presumably ignored with the connect session impl; only here for parity with other classic example
                 (master "local") 
                 (getOrCreate)))

;; this doesn't work for the connect session impl
;; see https://spark.apache.org/docs/3.5.4/spark-connect-overview.html
;; regarding spark context being deprecated
(.sparkContext session)

;; now start spark connect
;; /usr/local/spark-3.5.4-bin-hadoop3/sbin/start-connect-server.sh --packages org.apache.spark:spark-connect_2.12:3.5.4

;; this works if spark connect is started on the default port
(.. session
    (read)
    (option "sep" ":")
    (csv "/etc/passwd")
    (collect))

(.close session)

;; now stop spark connect
;; /usr/local/spark-3.5.4-bin-hadoop3/sbin/stop-connect-server.sh
