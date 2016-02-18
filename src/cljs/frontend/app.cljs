(ns frontend.app
  (:require-macros [cljs.core.async.macros :as a])
  (:require [reagent.core :as r]
            [cljs.core.async :as a]
            [reagent-dev-tools.state-tree :as dev-state]
            [ring.util.http-predicates :refer [success?]]
            [kekkonen.client.cqrs :as cqrs]))

(def empty-state
  {:session   {:status :unknown}
   :view      {:routing/id :permits}})

(defonce state (r/atom empty-state))
(dev-state/register-state-atom "App" state)

(def client (cqrs/create-client {:base-uri ""}))

;;
;; Access functions
;; Usable with r/track or to just access the data
;;

(defn view []
  (:view @state))

(defn session []
  (:session @state))

(defn current-user []
  (:user @(r/track session)))

(defn current-role []
  (:role @(r/track current-user)))

(defn users []
  (:users @state))

(defn user-by-id [id]
  (get @(r/track users) id))

(defn permits []
  (:permits @state))

(defn permit-by-id [id]
  (get @(r/track permits) id))

;;
;; Load stuff
;;

(defn map-by-id [k m]
  (into {} (map (juxt k identity) m)))

(defn load-users! []
  (a/go
    (let [resp (a/<! (cqrs/query client :users/get-all))]
      (when (success? resp)
        (swap! state assoc :users (map-by-id :user-id (:body resp)))))))

(defn load-my-permits! []
  (a/go
    (let [resp (a/<! (cqrs/query client :building-permit/my-permits))]
      (when (success? resp)
        (swap! state assoc :permits (map-by-id :permit-id (:body resp)))))))

(defn load-session! []
  (a/go
    (let [resp (a/<! (cqrs/query client :session/who-am-i))]
      (if (success? resp)
        (do
          (swap! state assoc :session {:status :logged
                                       :user (:body resp)})
          (load-my-permits!))
        (swap! state assoc :session {:status :not-logged})))))

(defn login! [username]
  (a/go
    (let [resp (a/<! (cqrs/command client :session/login {:username username}))]
      (if (success? resp)
        (a/<! (load-session!))))))

(defn logout! []
  (a/go
    (let [resp (a/<! (cqrs/command client :session/logout))]
      (if (success? resp)
        (a/<! (load-session!))))))

(defn navigate!
  ([route]
   (navigate! route nil))
  ([route params]
   (swap! state assoc :view (assoc params :routing/id route))))

(defn create-permit! [data]
  (a/go
    (let [resp (a/<! (cqrs/command client :building-permit/create-permit data))]
      (when (success? resp)
        (a/<! (load-my-permits!))
        (navigate! :permit {:id (:permit-id (:body resp))})))))

(defn claim-permit! [id]
  (a/go
    (let [resp (a/<! (cqrs/command client :building-permit/claim {:id id}))]
      (when (success? resp)
        (a/<! (load-my-permits!))))))

(defn add-comment [data]
  (a/go
    (let [resp (a/<! (cqrs/command client :building-permit/add-comment data))]
      (when (success? resp)
        ; FIXME: load single permit
        (a/<! (load-my-permits!))))))
