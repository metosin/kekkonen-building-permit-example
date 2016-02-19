(ns frontend.chord
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [<! put! close!]]
            [frontend.app :as app]))

(defonce conn (atom nil))

(defn create-url [path]
  (let [proto (.. js/window -location -protocol)
        host  (.. js/window -location -host)]
    (str (if (= "https:" proto) "wss" "ws") ":" host path)))

(defn connect [_]
  (go
    (when @conn (close! @conn))
    (let [{:keys [ws-channel error]} (<! (ws-ch (create-url "/ws") {:format :transit-json}))]
      (reset! conn ws-channel)
      (when-not error
        (loop [m (<! ws-channel)]
          (when-let [{{:keys [permit-id] :as message} :message} m]
            (js/console.log message)
            (app/load-permit! {:permit-id permit-id})
            (app/check-available-permit-actions! {:permit-id permit-id})
            (recur (<! ws-channel))))))))
