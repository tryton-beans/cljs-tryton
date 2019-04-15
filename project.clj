(defproject cljs-tryton "0.1.0"
  :description "clj/cljs client library for tryton"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [cljs-ajax "0.8.0"]
                 [org.clojure/data.codec "0.1.1"]
                 [org.clojure/core.async "0.4.490"]
                 [datawalk "0.1.12"]]
  :repl-options {:init-ns cljs-tryton.core})
