(ns geni.core)

;; This function would return the (mutable) singleton session of spark,
;; nothing more complex is needed.
;;
;; Even if you separately call builder.getOrCreate with some other options
;; and pass the result to do-something-with-session explicitly,
;; it is just mutating the same singleton session instance anyway.
;;
;; Also, this solution would work for both classic and spark connect cases
;; if we ensure that (.sparkContext session) is not called.
;; 
;; For unit tests, just use with-redefs if needed.
;;
;; If there are more complex requirements, maybe a dependency injection library is
;; the way to go.
;;
;; The only possible issue is waiting for the eager loading of the spark session,
;; but you can't do anything without a spark session anyway!
;; Even if you need a custom spark session, this will just mutate the (already loaded)
;; singleton which would probably have a negligible additional setup time cost.
(def default-session
  ;; this would call SparkSession.builder.getOrCreate()
  {:spark-session "default!"})

;; Further note, since the spark session is a singleton underneath,
;; even having a 2-arity for the "do-something-with-session" functions
;; could be misleading. Due to the singleton, any session passed in 
;; will be the same session instance anyway (possibly with mutated config).
;; As such, there is a further simplification to consider,
;; by removing the 2-arity form.
(defn do-something-with-session
  ([arg] 
   (do-something-with-session default-session arg))
  ([session arg] 
   (if (string? session)
     (println session)
     (dorun (map println [session arg])))))

