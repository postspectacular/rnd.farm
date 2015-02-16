(ns rndfarm.digest
  (:import
   (java.security NoSuchAlgorithmException MessageDigest)))

(defn int->byte
  [x] (byte (if (> x 127) (- x 256) x)))

(defn byte->int
  [x] (if (< x 0) (+ x 256) x))

(defn int->byte-array
  [x]
  (loop [acc (), x x]
    (if-not (zero? x)
      (recur (conj acc (int->byte (bit-and x 0xff)))
             (unsigned-bit-shift-right x 8))
      (byte-array (count acc) acc))))

(defn make-context
  ([] (MessageDigest/getInstance "SHA-256"))
  ([alg] (MessageDigest/getInstance alg)))

(defn update-single
  [^MessageDigest ctx input]
  (.update ctx (int->byte input))
  ctx)

(defn update-bytes
  [^MessageDigest ctx ^bytes input] (.update ctx input) ctx)

(defn digest
  [^MessageDigest ctx] (.digest ctx))

(defn bytes->hex
  [bytes] (apply str (map #(format "%02x" (byte->int %)) bytes)))

(defn digest-hex
  [^MessageDigest ctx] (bytes->hex (digest ctx)))

(defn sha256-sum
  "Computes SHA-256 of given utf-8 string."
  [input]
  (-> "SHA-256"
      (make-context)
      (update-bytes (.getBytes input "UTF-8"))
      (digest-hex)))
