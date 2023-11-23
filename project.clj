(defproject cljs-tryton "0.1.3"
  :description "clj/cljs client library for tryton"
  :url "https://github.com/fgui/cljs-tryton"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/clojurescript "1.11.60"] 
                 [cljs-ajax "0.8.0"]
                 [org.clojure/data.codec "0.1.1"]
                 [org.clojure/core.async "1.6.681"]
                 [datawalk "0.1.12"]
                 [mount "0.1.17"]
                 [clojure.java-time "1.3.0"]
                 [yogthos/config "1.2.0"]
                 ]
  :plugins [[lein-doo "0.1.7"]
            [cider/cider-nrepl "0.38.1"]
            [lein-cljsbuild "1.1.8"]
            [lein-ancient "1.0.0-RC3"]]
  
 :cljsbuild
  {:builds
   {:dev  {:source-paths ["src"]
           :compiler {:output-to "target/main.js"
                      :output-dir "target"
                      ;;; :source-map "target/main.js.map"
                      :optimizations :whitespace
                      :pretty-print true}}}})

