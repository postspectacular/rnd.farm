(ns rndfarm.core
  (:require
   [thi.ng.domus.core :as dom :refer [->px]]
   [thi.ng.domus.detect :as detect]
   [thi.ng.domus.utils :as utils]
   [thi.ng.domus.async :as async]
   [thi.ng.domus.log :refer [debug info warn]]
   [thi.ng.color.core :as col]
   [thi.ng.common.stringformat :as f]
   [cljs.core.async :refer [chan <! >! put! timeout close!]]
   [cljs.reader :as reader])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop]]))

(def max-events 2048)

(def state (atom nil))

(def int->hex
    (let [fmt ["#" (f/hex 6)]]
      (fn [i] (f/format fmt (bit-and i 0xffffff)))))

(defn init-generator
  [state]
  (let [{:keys [bus channels ws]} @state
        pos-chan (:pos-input channels)
        key-chan (:key-input channels)
        mpos (async/event-publisher bus js/window "mousemove" :pos-input)
        keys (async/event-publisher bus js/window "keypress" :key-input)]
    (go-loop [px 0 py 0, i 0]
      (let [[_ e] (<! pos-chan)]
        (if (< i max-events)
          (let [x (.-clientX e)
                y (.-clientY e)
                d (+ (- x px) (bit-shift-left (- y py) 8))
                ;;v (bit-xor (bit-shift-left (Math/abs (- x px)) 8) (Math/abs (- y py)))
                v (bit-xor (bit-shift-left x 8) y)
                v (Math/abs (bit-xor v (bit-shift-left d 16)))]
            (.send ws (pr-str [v x y (utils/now)]))
            (recur x y i))
          (do
            (info "remove mpos listener")
            (dom/remove-listeners [mpos])))))
    (go-loop []
      (let [[_ e] (<! key-chan)]
        (when e
          (.send ws (pr-str [(.-keyCode e) (utils/now)]))
          (recur))))))

(defn init-receiver
  [state]
  (let [{:keys [bus channels ws]} @state]
    (set! (.-onmessage ws) #(async/publish bus :receive (.-data %)))
    (go-loop [peers {}]
      (let [[_ e] (<! (:receive channels))]
        (when e
          (let [[id x y col] (reader/read-string e)
                peers (if-not (peers id)
                        (assoc peers id
                               (dom/create-dom!
                                [:div.cursor
                                 {:id id :style {:background-color (int->hex col)}}]
                                (.-body js/document)))
                        peers)]
            (dom/set-style! (peers id) (clj->js {:left (->px x) :top (->px y)}))
            (recur peers)))))))

(defn ws-app
  []
  (let [bus   (async/pub-sub
               ;;(fn [e] (debug :bus (first e) (second e)) (first e))
               first)
        chans (async/subscription-channels bus [:send :receive :pos-input :key-input])
        ws    (js/WebSocket. (aget js/window "__RND_WS_URL__"))]
    (reset! state
            {:bus bus
             :channels chans
             :ws ws
             :recording? false})
    (init-generator state)
    (init-receiver state)
    (dom/add-listeners
     [["#bt-record" "click" (fn [] (dom/add-class! (dom/by-id "main") "flipped"))]
      ["#bt-cancel" "click" (fn [] (dom/remove-class! (dom/by-id "main") "flipped"))]])))

(defn ^:export start
  []
  (if true ;;(detect/websocket?)
    (ws-app)))

(start)
