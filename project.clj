(defproject com.postspectacular/rnd-farm "0.1.0-SNAPSHOT"
  :description "Human generated randomness"
  :url "http://rnd.farm/"
  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2657"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [http-kit "2.1.16"]
                 [compojure "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [hiccup "1.0.5"]
                 [environ "1.0.0"]
                 [clj-time "0.9.0"]
                 [com.taoensso/timbre "2.3.0"]]

  :plugins [[lein-ring "0.8.13"]]

  :ring {:handler rndfarm.handler/app}
  :main rndfarm.handler
  
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}}

  :cljsbuild
  {:builds [{:id "dev"
             :source-paths ["src-cljs"]
             :compiler {:output-to "resources/public/js/app.js"
                        :optimizations :whitespace}}
            {:id "prod"
             :source-paths ["src-cljs"]
             :compiler {:output-to "resources/public/js/app.js"
                        :optimizations :advanced
                        :pretty-print false
                        ;;:preamble ["header.js"]
                        ;;:externs ["externs.js"]
                        }}]})
