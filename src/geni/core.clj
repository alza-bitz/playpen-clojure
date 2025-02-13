(ns geni.core
  (:require [geni.default :as gd]))

(defn do-something-with-session
  ([arg] 
   (do-something-with-session @gd/session arg))
  ([session arg] 
   (dorun (map println [session arg]))))

