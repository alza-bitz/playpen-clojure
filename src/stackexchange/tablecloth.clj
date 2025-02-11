(ns tablecloth
  (:require
   [clj-bom.core :as bom]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hato.client :as hc]
   [tablecloth.api :as tc]))

;; 1. Download the dataset https://archive.org/download/stackexchange/astronomy.stackexchange.com.7z

(with-open [in (:body (hc/get "https://archive.org/download/stackexchange/astronomy.stackexchange.com.7z"
                              {:as :stream
                               :http-client {:redirect-policy :always}}))
            out (-> "stackexchange/data/raw/astronomy.stackexchange.com.7z"
                    (doto (io/make-parents))
                    io/output-stream)]
  (io/copy in out))

(comment
  (def ^:private reader
    (bom/bom-reader "stackexchange/data/raw/Users.xml")))

(comment
  (.close reader))

(comment
  (->>
   (vector (xml/parse reader))
   (mapcat :content)
   (map :attrs)
   first))

(comment
  (def xs (sequence (comp (mapcat :content)
                          (map :attrs))
                    (vector (xml/parse reader))))

  (take 1 xs)

  (tc/dataset xs))

;; 2. Who are the top 10 users with reputation over 1000?

(with-open [users (bom/bom-reader "stackexchange/data/raw/Users.xml")]
  (->
   (sequence (comp (mapcat :content)
                   (map :attrs))
             (vector (xml/parse users)))
   (tc/dataset {:parser-fn {:Reputation :int32}})
   (tc/select-rows #(> (get % :Reputation) 5000))
   (tc/order-by :Reputation :desc)
   (tc/head 10)))
