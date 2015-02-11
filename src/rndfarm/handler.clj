(ns rndfarm.handler
  (:require
   [compojure.core :refer :all]
   [compojure.route :as route]
   [hiccup.page :refer [html5 include-js include-css]]
   [environ.core :refer [env]]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [ring.util.anti-forgery :refer [anti-forgery-field]]
   [ring.util.response :as resp]
   [clojure.java.io :as io]))

(def max-num (bit-shift-left 1 62))

(def formatter (java.text.DecimalFormat. "#,###,###,###,###,###,###"))

(defn as-long
  [n]
  (try
    (Long/parseUnsignedLong n)
    (catch Exception e)))

(defn style-number
  [n & [cls]]
  (let [h (nth [:h1 :h2 :h3 :h4 :h5] (long (rem n 5)))
        col (Long/toString n 16)
        col (subs col (max 0 (- (count col) 6)))
        px (* 100 (/ (bit-and n 1023) 1047.0))
        py (* 100 (/ (bit-and (unsigned-bit-shift-right n 10) 1023) 1047.0))]
    [h {:class (if cls (str "rnd " cls) "rnd")
        :style (format "color:#%s;left:%d%%;top:%d%%;" col (int px) (int py))} n]))

(defn persist-number
  [state n]
  (let [^java.io.Writer writer (:writer state)
        n' (style-number n)]
    (try
      (prn "adding number: " n)
      (.write writer (str n "\n"))
      (.flush writer)
      (-> state
          (assoc :last n)
          (update-in [:count] inc)
          (update-in [:pool] #(if (< (count %) 100)
                                (conj % n')
                                (conj (subvec % 1) n'))))
      (catch Exception e
        (.printStackTrace e)
        state))))

(defn read-numbers
  [stream]
  (with-open [r (-> stream io/input-stream io/reader)]
    (doall (line-seq r))))

(defonce store
  (let [stream  (env :rnd-stream)
        numbers (read-numbers stream)]
    (prn "read file: " stream (count numbers))
    (agent
     {:writer (io/writer stream :append true)
      :pool (mapv #(style-number (as-long %)) (take-last 100 numbers))
      :last (as-long (last numbers))
      :count (count numbers)})))

(defroutes app-routes
  (GET "/" [:as req]
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
          [:div#main
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
         (butlast (:pool @store))
         (style-number (:last @store) "rnd-last")]))

  (POST "/" [n]
        (if-let [n' (as-long n)]
          (do
            (send-off store persist-number n')
            (-> (resp/redirect "/")
                (assoc :flash {:type :ok :msg (str "Thanks, that's a great number: " n')})))
          (-> (resp/redirect "/")
              (assoc :flash {:type :err :msg "Hmmm.... that number wasn't so good!"}))))

  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
