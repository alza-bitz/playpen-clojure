(ns geni.client
  (:require
   [geni.core :as g]))

(g/session)

;; step 1. use cases with or without default session

(comment
  (g/do-something-with-session "hello"))

(comment
  (g/do-something-with-session {:spark-session
                                {:some-option "custom!"}}
                               "hello"))
