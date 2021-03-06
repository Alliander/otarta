(ns otarta.main
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [goog.crypt :as crypt]
   [huon.log :as log :refer [debug info warn error]]
   [otarta.core :as mqtt]
   [otarta.util :refer-macros [<err->]]
   websocket))


(set! js/WebSocket (.-w3cwebsocket websocket))


(def client (atom nil))


(defn handle-sub [broker-url topic-filter]
  (info :handle-sub :broker-url broker-url :topic-filter topic-filter)
  (go
    (reset! client (mqtt/client {:broker-url broker-url :keep-alive 10}))

    (let [[err {sub-ch :chan}] (<! (<err-> @client
                                           mqtt/connect
                                           (mqtt/subscribe topic-filter)))]
      (if err
        (do (error err) (println (str "Could not subscribe: " err)))
        (go-loop []
          (when-let [m (<! sub-ch)]
            (prn (assoc m :payload (crypt/byteArrayToString (:payload m))))
            (recur)))))))


(defn handle-pub [broker-url topic msg]
  (info :handle-pub :broker-url :topic topic :msg msg)
  (go
    (reset! client (mqtt/client {:broker-url broker-url}))

    (let [[err _] (<! (<err-> @client
                              mqtt/connect
                              (mqtt/publish topic msg)))]
      (when err
        (error err)
        (println (str "Could not publish: " err)))
      (mqtt/disconnect @client))))


(defn -main [& args]
  (let [argsv (vec args)]
    (when (= (last argsv) "-d")
      (log/set-root-level! :debug)
      (log/set-level! "otarta.octet-spec" :error)
      (log/enable!))

    (info :-main :args argsv)

    (condp = (first argsv)
      "pub" (apply handle-pub (subvec argsv 1 4))
      "sub" (apply handle-sub (subvec argsv 1 3))
      (println "Usage:\nsub: otarta.main sub <broker-url> <topic-filter> [-d]\npub: otarta.main pub <broker-url> <topic> <msg> [-d]\n"))))
