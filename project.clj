(defproject todoapp "0.1.0-SNAPSHOT"
  :description "TODO Application for Democracy Works"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :main ^:skip-aot todoapp.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
