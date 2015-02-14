(ns rndfarm.core
  (:require
   [thi.ng.domus.core :as dom :refer [->px]]
   [thi.ng.domus.detect :as detect]
   [thi.ng.domus.utils :as utils]
   [thi.ng.domus.async :as async]
   [thi.ng.domus.log :refer [debug info warn]]
   [thi.ng.color.core :as col]
   [thi.ng.common.math.core :as m]
   [thi.ng.common.stringformat :as f]
   [cljs.core.async :refer [chan <! >! put! timeout close!]]
   [cljs.reader :as reader])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(def max-bits 4096)

(def state (atom nil))

(defn encode-position
  [px py x y]
  (let [d (+ (- x px) (bit-shift-left (- y py) 8))
        v (bit-xor (bit-shift-left x 8) y)
        v (Math/abs (bit-xor v (bit-shift-left d 16)))]
    v))

(defn encode-key
  [x] x)

(defn update-rec-log
  [bits] (dom/set-text! (dom/by-id "reclog") (str bits " bits collected")))

(defn init-generator
  [state]
  (let [{:keys [bus ws]} @state
        [pos keys encode :as e-channels] (repeatedly 3 chan)
        ;;keys (async/event-channel bus js/window "keypress")
        listeners (dom/add-listeners
                   [[js/window "mousemove"
                     (fn [e]
                       (put! pos [(.-clientX e) (.-clientY e) (utils/now)]))]
                    [js/window "touchmove"
                     (fn [e]
                       (.preventDefault e)
                       (let [touches (.-touches e)
                             t (aget touches 0)]
                         (put! pos [(.-clientX t) (.-clientY t) (utils/now)])))]
                    [js/window "keypress"
                     (fn [e]
                       (put! keys [(.-keyCode e) (utils/now)]))]
                    ["#bt-cancel" "click" (fn [] (close! encode))]])]
    (update-rec-log 0)
    (go-loop [px 0, py 0]
      (if-let [e (<! pos)]
        (let [[x y t] e]
          (>! encode [(encode-position px py x y) t x y])
          (recur x y))
        (info "pos channel closed")))
    (go-loop []
      (if-let [e (<! keys)]
        (do (>! encode [(encode-key (first e)) (second e)])
            (recur))
        (info "key channel closed")))
    (go-loop [bits 0]
      (let [e (<! encode)]
        (if (and e (< bits max-bits))
          (let [bits (+ bits (Math/ceil (/ (Math/log (first e)) m/LOG2)))]
            (update-rec-log bits)
            (.send ws (pr-str e))
            (recur bits))
          (do
            (doseq [c e-channels] (close! c))
            (info "remove listeners")
            (dom/remove-listeners listeners)
            (dom/set-text! (dom/by-id "reclog") "Done.")
            (go
              (<! (timeout 500))
              (dom/remove-class! (dom/by-id "main") "flipped"))))))))

(defn init-receiver
  [state]
  (let [{:keys [bus channels ws]} @state]
    (set! (.-onmessage ws) #(async/publish bus :receive (.-data %)))
    (go-loop [peers {}]
      (let [[_ e] (<! (:receive channels))]
        (when e
          (let [[id x y col] (reader/read-string e)
                ;; TODO remove DIV when x& == -1000
                peers (if-not (peers id)
                        (assoc peers id
                               (dom/create-dom!
                                [:div.cursor
                                 {:id id :style {:background-color col}}]
                                (.-body js/document)))
                        peers)]
            (dom/set-style! (peers id) (clj->js {:left (->px x) :top (->px y)}))
            (recur peers)))))))

(defn start-recording
  [state]
  (dom/add-class! (dom/by-id "main") "flipped")
  (init-generator state))

(defn init-app
  []
  (let [bus   (async/pub-sub
               ;;(fn [e] (debug :bus (first e) (second e)) (first e))
               first)
        chans (async/subscription-channels bus [:send :receive])
        ws    (js/WebSocket. (aget js/window "__RND_WS_URL__"))]
    (reset! state
            {:bus bus
             :channels chans
             :ws ws
             :recording? false})
    (init-receiver state)
    (dom/add-listeners
     [["#bt-record" "click" (fn [] (start-recording state))]])))

(defn ^:export start
  []
  (if (aget js/window "WebSocket")
    (init-app)
    (set! (.-href (.-location js/window)) "/fallback")))

(start)
