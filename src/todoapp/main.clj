(ns todoapp.main
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [todoapp.app :as app])
  (:gen-class))

(defn -main
  ([]
   (-main nil))
  ([config-path]
   (let [config (edn/read-string (if config-path
                                   (slurp (io/file config-path))
                                   (slurp (io/resource "default-config.edn"))))]
     (println (str "Starting server on port " (:web-server-port config) ". Press ctrl+c to quit."))
     (component/start (app/todo-system config)))))