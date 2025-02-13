(ns geni.client 
  (:require
   [geni.core :as g]))

;; step 1. use cases with or without default session

(comment
  (g/do-something-with-session "hello"))

(comment
  (g/do-something-with-session {:spark-session "custom!"} "hello"))
