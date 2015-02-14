(ns rndfarm.handler
  (:require
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
   [taoensso.timbre :as timbre :refer [debug info warn error fatal]]))

(def mime default-mime-types)
(def max-num (dec (bit-shift-left 1 62)))
(def pool-size 100)

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

(defn persist-number
  [state n]
  (try
    (prn "adding number: " n)
    (let [^java.io.Writer writer (:writer state)
          n' (style-number n)]
      (.write writer (str n "\n"))
      (.flush writer)
      (-> state
          (assoc :last n)
          (update-in [:count] inc)
          (update-in [:pool] conj n)
          (update-in [:html-pool] conj-max n' pool-size)))
    (catch Exception e
      (.printStackTrace e)
      state)))

(defn read-numbers
  [stream]
  (with-open [r (-> stream io/input-stream io/reader)]
    (vec (line-seq r))))

(defonce store
  (let [stream (env :rnd-stream)
        pool   (read-numbers stream)]
    (prn :file stream :count (count pool))
    (agent
     {:writer    (io/writer stream :append true)
      :html-pool (mapv #(style-number (as-long %)) (take-last 100 pool))
      :pool      pool
      :last      (as-long (last pool))
      :count     (count pool)})))

(def channels (atom {}))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn ws-handler [req]
  (http/with-channel req channel
    (http/on-receive
     channel
     (fn [raw]
       (let [[v x y t0] (edn/read-string raw)
             t1 (tc/to-long (lt/local-now))
             hex    (Long/toString v 16)
             dt     (- t1 t0)]
         (info "ws received: " hex dt)
         (when-not (@channels channel)
           (info "new channel" channel)
           (swap! channels assoc channel {:col (rand-int 0xffffff) :uuid (uuid)}))
         ;; broadcast
         (let [{:keys [uuid col]} (@channels channel)
               payload (pr-str [uuid x y col])]
           (doseq [ch (keys @channels)]
             (http/send! ch payload))))))
    (http/on-close
     channel
     (fn [status]
       (info "ws closed: " channel status)
       (swap! channels dissoc channel)))))

(defroutes app-routes
  (GET "/old" [:as req]
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
             [:div.row-msg (.format formatter (:count @store)) " numbers in stream"])
           [:form {:method "post" :action "/"}
            (anti-forgery-field)
            [:div.row-xl
             [:input {:type "number" :name "n"
                      :placeholder "your random number"
                      :autofocus true
                      :min "0" :max max-num}]]
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
         (butlast (:html-pool @store))
         (style-number (:last @store) "rnd-last")]))

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
          [:div#trans-container
           [:div#main
            [:div.front
             [:div.row [:h1 "RND.FARM"]]
             [:div.row "A stream of human generated randomness"]
             [:div.row [:input#bt-record {:type "submit" :value "Record"}]]]
            [:div.back
             [:div.row [:h1 "Recording..."]]
             [:div.row [:input#bt-cancel {:type "submit" :value "Cancel"}]]]]]]
         (el/javascript-tag
          (format "var __RND_WS_URL__=\"ws://%s/ws\";var __RND_UID__=[%s];"
                  (env :rnd-server-name "localhost:3000")
                  ;;(env :rnd-server-name "192.168.1.68:3000")
                  ""))
         (include-js "/js/app.js")]))

  (GET "/ws" [] ws-handler)

  (GET "/random" [n :as req]
       (let [n (if-let [n (as-long n)] (min n 1000) 1)
             pool (:pool @store)
             nums (repeatedly n #(rand-nth pool))
             ^String accept (get-in req [:headers "accept"])]
         (cond
           (>= (.indexOf accept (mime "json")) 0)
           (-> (apply str (concat "[" (interpose \, nums) "]"))
               (resp/response)
               (resp/content-type (mime "json")))
           (>= (.indexOf accept "application/edn") 0)
           (-> (apply str (concat "[" (interpose \space nums) "]"))
               (resp/response)
               (resp/content-type (mime "edn")))
           :else (-> (apply str (interpose \, nums))
                     (resp/response)
                     (resp/content-type (mime "csv"))))))

  (GET "/snapshot" []
       (-> (resp/file-response (env :rnd-stream))
           (resp/content-type (mime "text/plain"))))

  (POST "/" [n]
        (if-let [n' (and (not (empty? n)) (as-long n))]
          (do
            (send-off store persist-number n')
            (-> (resp/redirect "/")
                (assoc :flash {:type :ok :msg (str "Thanks, that's a great number: " n')})))
          (-> (resp/redirect "/")
              (assoc :flash {:type :err :msg "Hmmm.... that number wasn't so good!"}))))

  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))

(defonce server (atom nil))

(defn stop! []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [& args]
  (reset! server (http/run-server #'app {:port 3000})))

(defn restart
  []
  (stop!)
  (Thread/sleep 200)
  (-main nil))
