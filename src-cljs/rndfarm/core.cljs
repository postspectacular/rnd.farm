(ns rndfarm.core
  (:require
   [thi.ng.domus.core :as dom :refer [->px]]
   [thi.ng.domus.detect :as detect]
   [thi.ng.domus.utils :as utils]
   [thi.ng.domus.async :as async]
   [thi.ng.domus.log :refer [debug info warn]]
   [cljs.core.async :refer [chan <! >! put! timeout]]
   [cljs.reader :as reader])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop]]))

(def state (atom nil))

(defn init-generator
  [state]
  (let [{:keys [bus channels ws]} @state
        pos (async/event-publisher bus js/window "mousemove" :pos-input)
        keys (async/event-publisher bus js/window "keypress" :key-input)]
    (go-loop [px 0 py 0 i 0]
      (let [[_ e] (<! (:pos-input channels))]
        (when e
          (let [x (.-clientX e)
                y (.-clientY e)
                v (bit-xor (bit-shift-left (Math/abs (- x px)) 8) (Math/abs (- y py)))
                v (bit-xor (bit-shift-left x 8) y)
                t (utils/now)
                i (+ (- x px) (bit-shift-left (- y py) 8))
                v2 (bit-xor v (bit-shift-left i 16))]
            (.send ws (pr-str {:msg [v v2 t]}))
            (recur x y (inc i))))))
    (go-loop []
      (let [[_ e] (<! (:key-input channels))]
        (when e
          (.send ws (pr-str {:msg [(.-keyCode e) (utils/now)]}))
          (recur))))))

(defn init-receiver
  [state]
  (let [{:keys [bus channels ws]} @state]
    (set! (.-onmessage ws) #(async/publish bus :receive (.-data %)))
    (go-loop []
      (let [[_ e] (<! (:receive channels))]
        (when e
          (info :received e)
          (recur))))))

(defn ws-app
  []
  (let [bus   (async/pub-sub
               (fn [e] (debug :bus (first e) (second e)) (first e))
               ;;first
               )
        chans (async/subscription-channels bus [:send :receive :pos-input :key-input])
        ws    (js/WebSocket. (aget js/window "__RND_WS_URL__"))]
    (reset! state
            {:bus bus
             :channels chans
             :ws ws
             :recording? false})
    (init-generator state)
    (init-receiver state)))

(defn ^:export start
  []
  (if true ;;(detect/websocket?)
    (ws-app)))

(start)
