(ns rndfarm.core
  (:require
   [thi.ng.domus.core :as dom :refer [->px]]
   [thi.ng.domus.utils :as utils]
   [thi.ng.domus.log :refer [debug info warn]]
   [thi.ng.common.stringformat :as f]
   [cljs.core.async :refer [chan <! >! put! timeout close!]]
   [cljs.reader :as reader])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(def MAX-BITS 8192)
(def LOG2 (Math/log 2))

(def MSG-REGISTER 0)
(def MSG-ENCODE 1)
(def MSG-HIDE 2)
(def MSG-DISCONNECT 3)

(def state (atom nil))

(def by-id (memoize dom/by-id))

(defn get-window-size
  [] [(.-innerWidth js/window) (.-innerHeight js/window)])

(defn window-resizer
  [state]
  (fn [e] (swap! state assoc :window-size (get-window-size))))

(defn encode-position
  [px py x y t]
  (let [d (+ (- x px) (bit-shift-left (- y py) 8))
        d (bit-xor d (bit-shift-left (bit-and t 0xff) 16))
        d (bit-xor d (bit-and (bit-shift-right t 8) 0xff))
        v (bit-xor (bit-shift-left x 8) y)
        v (Math/abs (bit-xor v (bit-shift-left d 16)))]
    v))

(defn encode-key
  [pk k pt t]
  (bit-xor (+ (bit-xor k pk) (bit-shift-left (- t pt) 16))
           (bit-and t 0xffff)))

(defn update-rec-log
  [bits] (dom/set-text! (by-id "reclog") (str bits " bits collected")))

(defn init-histogram
  [state]
  (->> (dom/set-html! (by-id "hist-wrapper") "")
       (dom/create-dom!
        [:svg
         {:width "90%" :viewBox "0 0 16 1.5"}
         [:g#axis 
          [:polyline
           {:points "0 1 16 1"
            :vector-effect "non-scaling-stroke"}]
          #_[:path
           {:d "M-0.2,0.75L0,0.75M-0.2,0.5L0,0.5M-0.2,0.25L0,0.25"
            :vector-effect "non-scaling-stroke"}]
          [:path
           {:d "M1,1L1,1.2M2,1L2,1.2M3,1L3,1.2M4,1L4,1.2M5,1L5,1.2M6,1L6,1.2M7,1L7,1.2M8,1L8,1.2M9,1L9,1.2M10,1L10,1.2M11,1L11,1.2M12,1L12,1.2M13,1L13,1.2M14,1L14,1.2M15,1L15,1.2"
            :vector-effect "non-scaling-stroke"}]]
         [:g#labels
          (for [x (range 16)]
            [:text {:x (+ x 0.5) :y 1.5} (f/format [(f/hex 2)] (* x 16))])]
         [:g#bins]]))
  (swap! state assoc :bins (vec (repeat 16 0))))

(defn int->bytes
  [v]
  (loop [acc (), v v]
    (if (pos? v)
      (recur (conj acc (bit-and v 0xff)) (unsigned-bit-shift-right v 8))
      acc)))

(defn update-histogram
  [state v]
  (let [bins (reduce
              (fn [bins v] (update-in bins [(bit-shift-right v 4)] inc))
              (:bins @state) (int->bytes v))
        peak (reduce max bins)
        el-bins (dom/by-id "bins")]
    (dom/set-html! el-bins "")
    (dorun
     (map-indexed
      (fn [i b]
        (let [h (/ b peak)]
          (dom/create-dom!
           [:rect {:x (+ i 0.1) :y (- 1 h) :width 0.8 :height h}]
           el-bins)))
      bins))
    (swap! state assoc :bins bins)))

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
    (init-histogram state)
    (update-rec-log 0)
    (go-loop [px 0, py 0]
      (if-let [e (<! pos)]
        (let [[x y t] e]
          (>! encode [(encode-position px py x y t) t x y])
          (recur x y))
        (info "pos channel closed")))
    (go-loop [pk 0, pt (utils/now)]
      (if-let [e (<! keys)]
        (let [[k t] e]
          (>! encode [(encode-key pk k pt t) t])
          (recur (+ pk (bit-shift-left k (bit-and pk 0xf))) t))
        (info "key channel closed")))
    (go-loop [bits 0]
      (let [e (<! encode)]
        (if (and e (< bits MAX-BITS))
          (let [v (first e)
                bits (+ bits (Math/ceil (/ (Math/log v) LOG2)))]
            (if (> (count e) 2)
              (let [[v t x y] e
                    [w h] (:window-size @state)]
                (.send ws (pr-str [MSG-ENCODE v t (int (* (/ x w) 1000)) (int (* (/ y h) 1000))])))
              (.send ws (pr-str (vec (cons MSG-ENCODE e)))))
            (update-rec-log bits)
            (update-histogram state v)
            (recur bits))
          (do
            (doseq [c e-channels] (close! c))
            (info "remove listeners")
            (dom/remove-listeners listeners)
            (dom/set-text! (by-id "reclog") "Done.")
            (.send ws (pr-str [MSG-HIDE]))
            (go
              (<! (timeout 500))
              (dom/remove-class! (by-id "main") "flipped"))))))))

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
  (let [{:keys [ws]} @state
        receiver (chan 4)]
    (set! (.-onmessage ws) #(put! receiver (.-data %)))
    (set! (.-onopen ws) (fn [e] (.send ws (pr-str [MSG-REGISTER]))))
    (go-loop [peers {}]
      (let [e (<! receiver)]
        (when e
          (let [msg (reader/read-string e)]
            ;;(info msg)
            (recur
             (case (first msg)
               1 (update-cursor peers (rest msg))
               3 (remove-cursor peers (rest msg))
               peers))))))))

(defn start-recording
  [state]
  (dom/add-class! (by-id "main") "flipped")
  (record-session state))

(defn init-app
  []
  (let [resize (window-resizer state)
        ws     (js/WebSocket. (aget js/window "__RND_WS_URL__"))]
    (reset! state
            {:ws ws
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
    (set! (.-href (.-location js/window)) "/")))

(start)
