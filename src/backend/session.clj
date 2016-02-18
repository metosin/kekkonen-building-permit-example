(ns backend.session
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]
            [backend.users :as users]))

(defn session-identity [req]
  (-> req :session :identity))

(defn require-session [_]
  (p/fnk [request :as m]
    (if (session-identity request)
      m
      (failure! {:status :bad-session}))))

(defn load-current-user [_]
  (p/fnk [request
          [:state users]
          :as m]
    (if-let [current-user (get @users (session-identity request))]
      (assoc-in m [:entities :current-user] current-user)
      (failure! {:status :bad-session}))))

(defn requires-role [allowed-roles]
  (p/fnk [[:entities [:current-user role]]
          :as m]
    (println (:entities m))
    (if (or (= :admin role)
            (contains? allowed-roles role))
      m)))

(p/defnk ^:command login
  [[:state users]
   [:data username :- s/Str]]
  (let [users-by-name (into {} (map (juxt :name identity) (vals @users)))]
    (if-let [user (get users-by-name username)]
      (-> (success {:status :ok})
          (assoc-in [:session :identity] (:id user)))
      (failure {:status :no-user}))))

(p/defnk ^:command logout
  []
  (assoc-in (success) [:session] nil))

(p/defnk ^:query who-am-i
  {:load-current-user true
   :responses {:default {:schema users/User}}}
  [[:entities current-user]]
  (success current-user))
