(ns rndfarm.generator
  (:require
   [clojure.java.io :as io])
  (:import
   [java.io InputStream]))

(defn int->byte [x] (if (> x 0x7f) (- x 0x100) x))
(defn byte->int [x] (if (neg? x) (+ x 0x100) x))

(defn read-int32-be
  [^InputStream in]
  (let [buf (byte-array 4)]
    (.read in buf 0 4)
    (bit-or
     (byte->int (aget buf 3))
     (bit-shift-left (byte->int (aget buf 2)) 8)
     (bit-shift-left (byte->int (aget buf 1)) 16)
     (bit-shift-left (byte->int (aget buf 0)) 24))))

(defn as-long
  [n]
  (try
    (Long/parseUnsignedLong n)
    (catch Exception e)))

(defn read-raw
  [path]
  (with-open [reader (-> path io/input-stream io/reader)]
    (->> reader
         (line-seq)
         (map as-long)
         (filter (complement nil?))
         (vec))))

(defn read-digest
  [path]
  (with-open [^InputStream in (-> path io/input-stream)]
    (loop [pool []]
      (if (>= (.available in) 4)
        (recur (conj pool (int (Math/abs (read-int32-be in)))))
        pool))))

(defn generator
  [pool seed]
  (let [size (count pool)]
    (atom
     {:pool (vec pool)
      :size size
      :idx (rem seed size)})))

(defn next-int
  [gen]
  (let [{:keys [pool idx size]} @gen
        x (pool idx)
        idx' (rem (inc idx) size)]
    (swap! gen assoc :idx idx')
    x))

(defn next-double
  [gen]
  (/ (double (next-int gen)) Integer/MAX_VALUE))
