(ns rndfarm.store
  (:require
   [rndfarm.digest :as dig]
   [clojure.core.async :as async :refer [go go-loop buffer chan <! >! timeout]]
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clj-time.coerce :as tc]
   [clj-time.local :as lt]
   [taoensso.timbre :as timbre :refer [debug info warn error fatal]]))

(defn chunking-digester
  "Creates an input channel to consume ints from and add them to a
  chunking MessageDigest. When chunk is filled, computes digest and
  puts its byte array into out chan. Returns input channel."
  [config out]
  (let [in (chan (buffer (:buf-size config)))
        chunk-size (dec (:chunk-size config))]
    (go
      (loop [ctx (dig/make-context (:algorithm config)), i 0]
        (if-let [x (<! in)]
          (let [buf (dig/int->byte-array x)]
            (info "add digest val: " x i)
            (dig/update-bytes ctx buf)
            (if (< i chunk-size)
              (recur ctx (+ i (count buf)))
              (let [d (dig/digest ctx)]
                (info "new digest chunk: " (dig/bytes->hex d))
                (>! out d)
                (recur (dig/make-context (:algorithm config)) 0))))
          (info "digester closed"))))
    in))

(defn digest-storage
  [config]
  (let [in (chan (buffer (:out-buf-size config)))
        ^java.io.OutputStream out (io/output-stream (:out-path config) :append true)]
    (go-loop []
      (if-let [^bytes digest (<! in)]
        (do
          (try
            (.write out digest 0 (alength digest))
            (.flush out)
            (info "wrote digest: " (dig/bytes->hex digest))
            (catch Exception e
              (.printStackTrace e)))
          (recur))
        (do
          (.close out)
          (info "digest-storage closed"))))
    in))

(defn raw-storage
  [config]
  (let [in (chan (buffer (:buf-size config)))
        ^java.io.Writer out (io/writer (:out-path config) :append true)]
    (go-loop []
      (if-let [x (<! in)]
        (do
          (try
            (.write out (str x "\n"))
            (.flush out)
            (catch Exception e
              (.printStackTrace e)))
          (recur))
        (do
          (.close out)
          (info "raw-storage closed"))))
    in))

(defn init-store
  [config]
  (let [in (chan)
        dig-out  (digest-storage (:digest config))
        raw-out  (raw-storage (:raw config))
        digester (chunking-digester (:digest config) dig-out)
        mult     (async/mult in)]
    (async/tap mult digester)
    (async/tap mult raw-out)
    {:input in
     :mult mult
     :consumers [raw-out dig-out digester]}))

(defn close-all!
  [store]
  (async/close! (:input store))
  (async/untap-all (:mult store))
  (doseq [c (:consumers store)] (async/close! c))
  (info "store closed"))

(defn new-input
  [store x]
  (info "enque: " x)
  (go (>! (:input store) x)))
