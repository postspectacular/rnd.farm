(defproject com.postspectacular/rnd-farm "2.0.0"
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
                 [com.taoensso/timbre "2.3.0"]
                 [thi.ng/domus "0.1.0-SNAPSHOT"]
                 [thi.ng/color "0.1.1-SNAPSHOT"]
                 [thi.ng/geom-core "0.3.0-SNAPSHOT"]
                 [thi.ng/geom-types "0.3.0-SNAPSHOT"]
                 [thi.ng/geom-svg "0.3.0-SNAPSHOT"]]

  :ring {:handler rndfarm.handler/app}
  :main rndfarm.handler

  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]
         :plugins      [[lein-ring "0.8.13"]
                        [lein-cljsbuild "1.0.4"]]}}

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
