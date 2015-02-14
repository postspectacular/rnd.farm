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

(def MAX-BITS 8192)

(def MSG-REGISTER 0)
(def MSG-ENCODE 1)
(def MSG-HIDE 2)
(def MSG-DISCONNECT 3)

(def state (atom nil))

(defn get-window-size
  [] [(.-innerWidth js/window) (.-innerHeight js/window)])

(defn window-resizer
  [state]
  (fn [e] (swap! state assoc :window-size (get-window-size))))

(defn encode-position
  [px py x y]
  (let [d (+ (- x px) (bit-shift-left (- y py) 8))
        v (bit-xor (bit-shift-left x 8) y)
        v (Math/abs (bit-xor v (bit-shift-left d 16)))]
    v))

(defn encode-key
  [x pt t] x)

(defn update-rec-log
  [bits] (dom/set-text! (dom/by-id "reclog") (str bits " bits collected")))

(defn record-session
  [state]
  (let [ws (:ws @state)
        [pos keys encode :as e-channels] (repeatedly 3 chan)
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
    (go-loop [pt 0]
      (if-let [e (<! keys)]
        (let [[k t] e]
          (>! encode [(encode-key k pt t) t])
          (recur t))
        (info "key channel closed")))
    (go-loop [bits 0]
      (let [e (<! encode)]
        (if (and e (< bits MAX-BITS))
          (let [bits (+ bits (Math/ceil (/ (Math/log (first e)) m/LOG2)))]
            (update-rec-log bits)
            (if (> (count e) 2)
              (let [[v t x y] e
                    [w h] (:window-size @state)]
                (.send ws (pr-str [MSG-ENCODE v t (int (* (/ x w) 1000)) (int (* (/ y h) 1000))])))
              (.send ws (pr-str (vec (cons MSG-ENCODE e)))))
            (recur bits))
          (do
            (doseq [c e-channels] (close! c))
            (info "remove listeners")
            (dom/remove-listeners listeners)
            (dom/set-text! (dom/by-id "reclog") "Done.")
            (.send ws (pr-str [MSG-HIDE]))
            (go
              (<! (timeout 500))
              (dom/remove-class! (dom/by-id "main") "flipped"))))))))

(defn update-cursor
  [peers [id x y col]]
  (let [peers (if (peers id)
                peers
                (assoc peers id
                       (dom/create-dom!
                        [:div.cursor
                         {:id id :style {:background-color col}}]
                        (.-body js/document))))]
    (dom/set-style!
     (peers id)
     (clj->js {:left (str (* x 0.1) "%") :top (str (* y 0.1) "%")}))
    peers))

(defn remove-cursor
  [peers [id]]
  (when-let [el (peers id)]
    (dom/remove! el))
  (dissoc peers id))

(defn init-receiver
  [state]
  (let [{:keys [bus channels ws]} @state]
    (set! (.-onmessage ws) #(async/publish bus :receive (.-data %)))
    (set! (.-onopen ws) (fn [e] (.send ws (pr-str [MSG-REGISTER]))))
    (go-loop [peers {}]
      (let [[_ e] (<! (:receive channels))]
        (when e
          (let [msg (reader/read-string e)]
            (info msg)
            (recur
             (case (first msg)
               1 (update-cursor peers (rest msg))
               3 (remove-cursor peers (rest msg))
               peers))))))))

(defn start-recording
  [state]
  (dom/add-class! (dom/by-id "main") "flipped")
  (record-session state))

(defn init-app
  []
  (let [bus   (async/pub-sub
               ;;(fn [e] (debug :bus (first e) (second e)) (first e))
               first)
        chans (async/subscription-channels bus [:send :receive])
        resize (window-resizer state)
        ws    (js/WebSocket. (aget js/window "__RND_WS_URL__"))]
    (reset! state
            {:bus bus
             :channels chans
             :ws ws
             :recording? false})
    (init-receiver state)
    (resize)
    (dom/add-listeners
     [[js/window "resize" resize]
      ["#bt-record" "click" (fn [] (start-recording state))]])))

(defn ^:export start
  []
  (if (aget js/window "WebSocket")
    (init-app)
    (set! (.-href (.-location js/window)) "/fallback")))

(start)
