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

(defn available-actions []
  (:available-permit-actions @state))

(def actions [:building-permit/open
              :building-permit/submit
              :building-permit/claim
              :building-permit/return-to-applicant
              :building-permit/approve
              :building-permit/reject])

(defn interesting-actions []
  (let [available-actions (set (keys (filter (comp nil? val) @(r/track available-actions))))]
    (vec (filter available-actions actions))))

(defn available-action? [action]
  (nil? (get @(r/track available-actions) action)))

;;
;; Load stuff
;;

(defn map-by-id [k m]
  (into {} (map (juxt k identity) m)))

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
                                       :user (:body resp)}))
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

(defmulti navigate-hook :routing/id)

(defmethod navigate-hook :default [_] nil)

(defn navigate!
  ([route]
   (navigate! route nil))
  ([route params]
   (swap! state assoc :view (assoc params :routing/id route))
   (navigate-hook (assoc params :routing/id route))))

(defn create-permit! [data]
  (a/go
    (let [resp (a/<! (cqrs/command client :building-permit/create-permit data))]
      (when (success? resp)
        (a/<! (load-my-permits!))
        (navigate! :permit {:permit-id (:permit-id (:body resp))})))))

(defn check-available-permit-actions! [data]
  (a/go
    (let [resp (a/<! (cqrs/actions client :building-permit data))]
      (when (success? resp)
        (swap! state assoc :available-permit-actions (:body resp))))))

(defn permit-action! [k data]
  (a/go
    (let [resp (a/<! (cqrs/command client k data))]
      (check-available-permit-actions! (select-keys data [:permit-id]))
      (load-my-permits!))))
