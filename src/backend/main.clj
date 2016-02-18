(ns backend.main
  (:require [com.stuartsierra.component :as component]
            [reloaded.repl :refer [set-init! go]])
  (:gen-class))

(defn -main [& [port]]
  (let [port (or port 3000)]
    (require 'backend.system)
    (set-init! #((resolve 'backend.system/new-system) {:http {:port port}}))
    (go)))
