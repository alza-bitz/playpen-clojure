(ns geni.client 
  (:require
   [clojure.tools.namespace.reload :as tnr]
   [geni.core :as g]
   [geni.default] ;; only including so we can inspect value
   ))

;; step 1. assumption. default-session ns not loaded

(reverse (loaded-libs))

@geni.default/session

;; step 1. use cases without default session loaded

(comment
  (g/do-something-with-session "hello"))

(comment
  (g/do-something-with-session {:spark-session "custom!"} "hello"))

;; now (re)load default session ns

(comment
  (require '[geni.default-session] :verbose :reload))

;; step 2 assumption. default-session ns now loaded

(reverse (loaded-libs))

@geni.default/session

;; step 2. use cases with default session loaded

(comment
  (g/do-something-with-session "hello"))

(comment
  (g/do-something-with-session {:spark-session "custom!"} "hello"))

;; clean up before starting over

(comment
  (reset! geni.default/session "please load the default ns if you want to use the default session."))

(comment
  (reverse (tnr/remove-lib 'geni.default-session)))
