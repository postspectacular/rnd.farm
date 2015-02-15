(ns rndfarm.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [environ.core :refer [env]]))

(defn load-config
  [path]
  (-> path (io/reader) (java.io.PushbackReader.) (edn/read)))

(def config (load-config (env :rnd-config "config.edn")))
