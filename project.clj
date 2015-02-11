(defproject com.postspectacular/rnd-farm "0.1.0-SNAPSHOT"
  :description "Human generated randomness"
  :url "http://rnd.farm/"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [hiccup "1.0.5"]
                 [environ "1.0.0"]]
  :plugins [[lein-ring "0.8.13"]]
  :ring {:handler rndfarm.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
