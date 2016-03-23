(defproject todoapp "0.1.0-SNAPSHOT"
  :description "TODO Application for Democracy Works"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.datomic/datomic-free "0.9.5350"]
                 [hiccup "1.0.5"]
                 [io.rkn/conformity "0.4.0"]
                 [ring "1.4.0"]]
  :main ^:skip-aot todoapp.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[clj-http "2.1.0"]]}})
