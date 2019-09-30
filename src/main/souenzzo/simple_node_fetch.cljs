(ns souenzzo.simple-node-fetch
  (:require ["http2" :as http]
            [clojure.core.async :as async]))

(defn fetch
  [authority path]
  (let [c (async/promise-chan)
        headers-chan (async/promise-chan)
        client (doto (http/connect authority)
                 (.on "error" (fn [err]
                                (async/put! c err))))
        body-state (atom "")
        req (.request client #js{":path" path})]
    (doto req
      (.setEncoding "utf8")
      (.on "response" (fn [headers flags]
                        (async/put! headers-chan (into {} (js->clj (js/Object.entries headers))))))
      (.on "data" (fn [chunk]
                    (swap! body-state (fn [old-data] (str old-data chunk)))))
      (.on "end" (fn []
                   (async/go
                     (let [body @body-state
                           headers (async/<! headers-chan)]
                       (.close req)
                       (async/>! c {::body    body
                                    ::headers headers})))))
      (.end))
    c))

(defn ok
  []
  (async/go
    (prn (async/<! (fetch "https://google.com" "/")))))

(defn after-load
  []
  (ok))
