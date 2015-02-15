(ns rndfarm.digest
  (:import
   (java.security NoSuchAlgorithmException MessageDigest)))

(defn make-context
  ([] (MessageDigest/getInstance "SHA-256"))
  ([alg] (MessageDigest/getInstance alg)))

(defn update-single
  [^MessageDigest ctx input]
  (.update ctx (byte (if (> input 127) (- input 255) input))))

(defn update-bytes
  [^MessageDigest ctx ^bytes input] (.update ctx input))

(defn digest
  [^MessageDigest ctx] (.digest ctx))

(defn digest-hex
  [^MessageDigest ctx]
  (apply str (map #(format "%02x" (bit-and % 0xff)) (digest ctx))))

(defn sha256-sum
  "Computes SHA-256 of given string."
  [input]
  (let [md (make-context "SHA-256")]
    (.update md (.getBytes input))
    (digest-hex md)))
