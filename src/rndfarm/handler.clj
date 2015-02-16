(ns rndfarm.handler
  (:require
   [rndfarm.config :refer [config]]
   [rndfarm.store :as store]
   [org.httpkit.server :as http]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [hiccup.core :refer [html]]
   [hiccup.page :refer [html5 include-js include-css]]
   [hiccup.element :as el]
   [environ.core :refer [env]]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [ring.util.anti-forgery :refer [anti-forgery-field]]
   [ring.util.response :as resp]
   [ring.util.mime-type :refer [default-mime-types]]
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clj-time.coerce :as tc]
   [clj-time.local :as lt]
   [thi.ng.color.core :as col]
   [thi.ng.common.math.core :as m]
   [taoensso.timbre :as timbre :refer [debug info warn error fatal]]))

(defonce state (atom {}))

(def mime default-mime-types)

(def formatter (java.text.DecimalFormat. "#,###,###,###,###,###,###"))

(defn as-long
  [n]
  (try
    (Long/parseUnsignedLong n)
    (catch Exception e)))

(defn conj-max
  [vec x limit]
  (if (< (count vec) limit)
    (conj vec x)
    (conj (subvec vec 1) x)))

(defn style-number
  [n & [cls]]
  (let [h (nth [:h1 :h2 :h3 :h4 :h5] (rem n 5))
        col (Long/toString n 16)
        col (subs col (max 0 (- (count col) 6)))
        px (* 100 (/ (bit-and n 1023) 1047.0))
        py (* 100 (/ (bit-and (unsigned-bit-shift-right n 10) 1023) 1047.0))]
    (html
     [h {:class (if cls (str "rnd " cls) "rnd")
         :style (format "color:#%s;left:%d%%;top:%d%%;" col (int px) (int py))} n])))

(defn read-numbers
  [stream]
  (with-open [r (-> stream io/input-stream io/reader)]
    (vec (line-seq r))))

(defn add-number
  [x]
  (store/new-input (:store @state) x)
  (swap! state
         (fn [state]
           (-> state
               (update-in [:html-pool] conj-max (style-number x) (:html-pool-size config))
               (update-in [:pool] conj-max x (:raw-pool-size config))
               (assoc :last x)
               (update-in [:count] inc)))))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn all-channels [] (keys (:channels @state)))

(defn get-channel [c] (get-in @state [:channels c]))

(defn register-channel
  [channel]
  (info "new channel" channel)
  (swap! state assoc-in [:channels channel]
         {:col (col/hsva->css (m/random) (m/random 0.8 1) (m/random 0.5 1))
          :uuid (uuid)}))

(defn process-register-message
  [channel]
  (let [channels (:channels @state)]
    (register-channel channel)
    (doseq [[ch cv] channels]
      (when-let [pos (cv :pos)]
        (http/send! channel (pr-str [1 (:uuid cv) (pos 0) (pos 1) (:col cv)]))))))

(defn process-encode-message
  [channel msg]
  (let [[v t0 x y] msg
        t1   (tc/to-long (lt/local-now))
        hex (Long/toString v 16)
        dt  (- t1 t0)]
    (info "ws received: " hex dt)
    (when-not (get-channel channel)
      (register-channel channel))
    (swap! state assoc-in [:channels channel :pos] [x y])
    (add-number v)
    ;; broadcast
    (if (and x y)
      (let [{:keys [uuid col]} (get-channel channel)
            payload (pr-str [1 uuid x y col])]
        (doseq [ch (all-channels)]
          (http/send! ch payload))))))

(defn process-hide-message
  [channel]
  (let [{:keys [uuid col]} (get-channel channel)
        payload (pr-str [1 uuid -1000 -1000 col])]
    (swap! state assoc-in [:channels channel :pos] [-1000 -1000])
    (info "ws hide: " channel uuid)
    (doseq [ch (all-channels)]
      (http/send! ch payload))))

(defn process-disconnect
  [channel]
  (info "ws disconnected: " channel)
  (let [{:keys [uuid col]} (get-channel channel)
        payload (pr-str [3 uuid])]
    (swap! state update-in [:channels] dissoc channel)
    (doseq [ch (all-channels)]
      (http/send! ch payload))))

(defn ws-handler [req]
  (http/with-channel req channel
    (http/on-receive
     channel
     (fn [raw]
       (let [msg (edn/read-string raw)]
         (case (first msg)
           0 (process-register-message channel)
           1 (process-encode-message channel (rest msg))
           2 (process-hide-message channel)
           (warn "unknown msg type: " msg)))))
    (http/on-close
     channel
     (fn [status]
       (process-disconnect channel)))))

(defn numbers-mime-response
  [nums mtype start end sep]
  (-> (apply str (concat start (interpose sep nums) end))
      (resp/response)
      (resp/content-type (mime mtype))))

(def include-piwik
  (html
   (el/javascript-tag
    "var _paq = _paq || []; _paq.push(['trackPageView']); _paq.push(['enableLinkTracking']);(function(){var u='//rnd.farm:8080/'; _paq.push(['setTrackerUrl',u+'piwik.php']); _paq.push(['setSiteId',1]); var d=document, g=d.createElement('script'), s=d.getElementsByTagName('script')[0]; g.type='text/javascript'; g.async=true; g.defer=true; g.src=u+'piwik.js'; s.parentNode.insertBefore(g,s);})();")))

(defroutes app-routes
  (GET "/" [:as req]
       ;;(info req)
       (html5
        {:lang "en"}
        [:head
         [:meta {:charset "utf-8"}]
         [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
         [:meta {:name "viewport" :content "width=device-width,initial-scale=1"}]
         [:meta {:name "author" :content "Karsten Schmidt, PostSpectacular"}]
         [:meta {:name "description" :content "A stream of human generated randomness"}]
         [:title "rnd.farm"]
         (include-css "http://fonts.googleapis.com/css?family=Inconsolata" "/css/main.min.css")]
        [:body
         [:div.container
          [:div#main-old
           [:div.row [:h1 "RND.FARM"]]
           [:div.row "A stream of human generated randomness"]
           (if-let [flash (:flash req)]
             [:div {:class (str "row-msg msg-" (name (:type flash)))} (:msg flash)]
             [:div.row-msg (.format formatter (:count @state)) " numbers in stream"])
           [:form {:method "post" :action "/"}
            (anti-forgery-field)
            [:div.row-xl
             [:input {:type "number" :name "n"
                      :placeholder "your random number"
                      :autofocus true
                      :min "0" :max (:max-num config)}]]
            [:div.row [:input {:type "submit"}]]]
           [:div.row.row-footer
            (interpose
             " &middot; "
             [#_[:a {:href "#"} "About"]
              #_[:a {:href "#" :title "Access forthcoming"} "API"]
              [:a {:href "https://github.com/postspectacular/rnd.farm/blob/master/README.md"} "About / GitHub"]])
            [:br]
            " &copy; 2015 "
            [:a {:href "http://postspectacular.com"} "postspectacular.com"]]]]
         (butlast (:html-pool @state))
         (style-number (:last @state) "rnd-last")]))

  (GET "/new" [:as req]
       ;;(info req)
       (html5
        {:lang "en"}
        [:head
         [:meta {:charset "utf-8"}]
         [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
         [:meta {:name "viewport" :content "width=device-width,initial-scale=1"}]
         [:meta {:name "author" :content "Karsten Schmidt, PostSpectacular"}]
         [:meta {:name "description" :content "A stream of human generated randomness"}]
         [:title "rnd.farm"]
         (include-css "http://fonts.googleapis.com/css?family=Inconsolata" "/css/main.min.css")]
        [:body
         [:div.container
          [:div#trans-container
           [:div#main
            [:div.front
             [:div.row [:h1 "RND.FARM"]]
             [:div.row "A stream of human generated randomness"]
             [:div.row [:button#bt-record "Record"]]]
            [:div.back
             [:div.row [:h1 "Recording..."]]
             [:div#reclog.row]
             [:div#hist-wrapper.row-xl]
             [:div.row [:button#bt-cancel "Cancel"]]]]]]
         (el/javascript-tag
          (format "var __RND_WS_URL__=\"ws://%s/ws\";"
                  ;;(env :rnd-server-name "localhost:3000")
                  (:server-name config)))
         (include-js "/js/app.js")
         include-piwik]))

  (GET "/ws" [] ws-handler)

  (GET "/random" [n :as req]
       (let [n (if-let [n (as-long n)] (max (min n 1000) 1) 1)
             pool (:pool @state)
             nums (repeatedly n #(rand-nth pool))
             ^String accept (get-in req [:headers "accept"])]
         (cond
           (>= (.indexOf accept (mime "json")) 0)
           (numbers-mime-response nums "json" "[" "]" \,)
           (>= (.indexOf accept "application/edn") 0)
           (numbers-mime-response nums "edn" "[" "]" \space)
           :else (numbers-mime-response nums "csv" "" "" \,))))

  (GET "/snapshot" []
       (-> (resp/file-response (-> config :raw :out-path))
           (resp/content-type (mime "text/plain"))))

  (POST "/" [n]
        (if-let [n' (and (not (empty? n)) (as-long n))]
          (do
            (add-number n')
            (-> (resp/redirect "/")
                (assoc :flash {:type :ok :msg (str "Thanks, that's a great number: " n')})))
          (-> (resp/redirect "/")
              (assoc :flash {:type :err :msg "Hmmm.... that number wasn't so good!"}))))

  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))

(defn init-state
  []
  (let [raw (-> config :raw :out-path)
        pool   (read-numbers raw)]
    (prn :file raw :count (count pool))
    (reset! state
     {:html-pool (mapv #(style-number (as-long %)) (take-last (:html-pool-size config) pool))
      :pool      (vec (take-last (:raw-pool-size config) pool))
      :last      (as-long (peek pool))
      :count     (count pool)
      :store     (store/init-store config)
      :channels  {}})))

(defn stop! []
  (when-let [store (:store @state)]
    (store/close-all! store)
    (swap! state dissoc :store))
  (when-let [server (:server @state)]
    (server :timeout 100)
    (swap! state dissoc :server)))

(defn -main [& args]
  (init-state)
  (swap! state assoc :server (http/run-server #'app {:port 3000})))

(defn restart
  []
  (stop!)
  (Thread/sleep 200)
  (-main nil))
