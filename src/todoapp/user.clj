(ns todoapp.user
  (:require [com.stuartsierra.component :as component]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [todoapp.app :as app]))

(def system nil)

(defn new-system
  [config]
  (alter-var-root #'system (constantly (app/todo-system config))))

(defn start-system
  []
  (alter-var-root #'system component/start))

(defn stop-system
  []
  (alter-var-root #'system component/stop))

(comment
  (new-system (edn/read-string (slurp (io/resource "default-config.edn"))))
  (start-system)
  (stop-system)
  )