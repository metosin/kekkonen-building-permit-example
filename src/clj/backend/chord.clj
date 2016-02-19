(ns backend.chord
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [go >! <!]]))

(defrecord Chord [clients]
  component/Lifecycle
  (start [this]
    (if-not clients
      (assoc this :clients (atom {}))))
  (stop [this]
    this))

(defn broadcast [chord m]
  (go
    (doseq [x (vals @(:clients chord))]
      (>! x m))))

(defn create []
  (map->Chord {}))

(defn join [chord ws-ch]
  (swap! (:clients chord) assoc ws-ch ws-ch))
