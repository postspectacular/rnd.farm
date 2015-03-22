(defproject com.postspectacular/rnd-farm "2.0.0"
  :description  "Human generated randomness"
  :url          "http://rnd.farm/"
  :license      {:name "Apache Software License"
                 :url "http://www.apache.org/licenses/LICENSE-2.0"
                 :distribution :repo}
  :scm          {:name "git"
                 :url  "https://github.com/postspectacular/rnd.farm"}
  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3117"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [http-kit "2.1.16"]
                 [compojure "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [hiccup "1.0.5"]
                 [environ "1.0.0"]
                 [clj-time "0.9.0"]
                 [com.taoensso/timbre "2.3.0"]
                 [thi.ng/common "0.3.1"]
                 [thi.ng/domus "0.1.0"]
                 [thi.ng/color "0.1.2"]]

  :main         rndfarm.handler

  :profiles     {:dev  {:plugins  [[lein-cljsbuild "1.0.5"]]}
                 :prod {:jvm-opts ["-server" "-Xms1g" "-Xmx3g"]}}

  :cljsbuild    {:builds
                 [{:id "dev"
                   :source-paths ["src-cljs"]
                   :compiler {:output-to "resources/public/js/app.js"
                              :optimizations :whitespace}}
                  {:id "prod"
                   :source-paths ["src-cljs"]
                   :compiler {:output-to "resources/public/js/app.js"
                              :optimizations :advanced
                              :pretty-print false}}]})
