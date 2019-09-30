(ns user
  (:require [cognitect.transit :as transit]
            [shadow.cljs.devtools.server :as shadow.server]
            [shadow.cljs.devtools.api :as shadow.api]
            [clojure.java.io :as io])
  (:import (java.io ByteArrayOutputStream)))

(defn cljs
  []
  (shadow.server/start!)
  (shadow.api/watch :simple-node-fetch))

(defn pr-transit-str
  [type o]
  (let [boas (ByteArrayOutputStream.)]
    (transit/write (transit/writer boas type) o)
    (str boas)))



(defn parse-transit-str
  [type s]
  (transit/read (transit/reader (io/input-stream (.getBytes s))
                                type)))


(defn json->edn
  [x]
  (reduce (fn [{:keys [array?]
                :as   acc} c]
            (case c
              123 (assoc acc
                    :stack {}
                    :map? true)
              125 (assoc acc
                    :map? false)
              91 (assoc acc
                   :stack []
                   :array? true)
              93 (assoc acc
                   :array? false)
              49 (cond
                   array? (-> acc
                              (assoc :number? true)
                              (update :stack conj 1)))
              (do
                (prn c)
                acc)))
          {}
          (.getBytes x)))