(ns geni.default-session
  (:require [geni.default :as gd]))

(do
 (println "loading default session!")
 (reset! gd/session {:spark-session "default!"}))