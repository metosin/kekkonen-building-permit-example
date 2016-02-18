(ns backend.main
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [reloaded.repl :as reloaded])
  (:gen-class))

(defn init
  ([] (init nil))
  ([opts]
   (require 'backend.system)
   ((resolve 'backend.system/new-system) (merge {:http {:port 3000}
                                                 :dev-mode? true}
                                                opts))))

(defn setup-app! [opts]
  (reloaded/set-init! #(init opts)))

(defn -main [& args]
  (log/info "Application starting...")
  (setup-app! nil)
  (reloaded/go)
  (log/info "Application running!"))
