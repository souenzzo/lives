(ns souenzzo.telegram-ipfs-bot.main
  (:require [clj-http.client :as http]
            [clojure.string :as string]
            [clojure.core.async :as async]))

(def telegram-token (string/trim (System/getenv "TELEGRAM_TOKEN")))
(defn xf-distinct-by
  [f]
  (fn [rf]
    (let [seen (volatile! #{})]
      (fn
        ;; init
        ([] (rf))
        ;; finalize
        ([coll]
         (rf coll))
        ;; accumulate
        ([acc el]
         (let [x (f el)]
           (if (contains? @seen x)
             acc
             (do
               (vswap! seen conj x)
               (rf acc el)))))))))

(defn request-async
  [req]
  (let [chan (async/promise-chan)
        deliver (partial async/put! chan)]
    (-> req
        (assoc :async? true)
        (http/request deliver deliver))
    chan))

(comment
  ;; sample request
  (-> (http/request {:method :get
                     :as     :json
                     :url    (format "https://api.telegram.org/bot%s/getUpdates"
                                     telegram-token)})
      :body
      :result
      first)
  =>
  {:update_id 511080974
   :message   {:message_id 512
               :from       {:id            116632598
                            :is_bot        false
                            :first_name    "Enzzo"
                            :username      "souenzzo"
                            :language_code "en"}
               :chat       {:id 116632598 :first_name "Enzzo" :username "souenzzo" :type "private"}
               :date       1567642337
               :text       "/get QmbofmZHtgHCVvtRSFsfApS3L32AW1D6EQqCDEQa9bXW48"
               :entities   [{:offset 0 :length 4 :type "bot_command"}]}}
  (-> (http/request {:method :get
                     :url    "http://localhost:5002/ipfs/QmbofmZHtgHCVvtRSFsfApS3L32AW1D6EQqCDEQa9bXW48"})
      :body)
  =>
  "ok")

(defn handle-message
  [{{:keys [id]} :chat
    :keys        [text message_id]}]
  (when (string/starts-with? text "/get ")
    (let [hash (string/trim (subs text 4))]
      {:em-respota {:reply_to_message_id message_id
                    :chat_id             id}
       :request    {:method :get
                    :url    (format "http://localhost:5002/ipfs/%s"
                                    hash)}})))

(defn handle-get-ipfs
  [{{:keys [body]}                        :response
    {:keys [reply_to_message_id chat_id]} :em-respota}]
  {:method      :post
   :as          :json
   :url         (format "https://api.telegram.org/bot%s/sendMessage"
                        telegram-token)
   :form-params {:reply_to_message_id reply_to_message_id
                 :chat_id             chat_id
                 :text                body}})


(defonce updates-channel
         (let [c (async/chan 1024 (xf-distinct-by :update_id))]
           (async/go-loop []
             (async/<! (async/timeout 10000))
             (let [result (-> {:method :get
                               :as     :json
                               :url    (format "https://api.telegram.org/bot%s/getUpdates"
                                               telegram-token)}
                              request-async
                              async/<!)]
               (prn [:updates-ok! result])
               (doseq [i (-> result :body :result)]
                 (async/>! c i)))
             (recur))
           c))

(defonce pendentes
         (let [c (async/chan)]
           (async/go-loop []
             (let [update (async/<! updates-channel)]
               (when-let [x (-> update
                                :message
                                handle-message)]
                 (prn [:get-ipfs handle-message])
                 (async/>! c
                           (assoc x
                             :response (async/<! (request-async (:request x))))))
               (recur)))
           c))


(defonce response-channel
         (let [c (async/chan)]
           (async/go-loop []
             (let [x (async/<! pendentes)]
               (when-let [req (handle-get-ipfs x)]
                 (prn [:responsendo req])
                 (async/<! (request-async req))))
             (recur))
           c))



(defn -main
  [& _]
  (prn :ok))
