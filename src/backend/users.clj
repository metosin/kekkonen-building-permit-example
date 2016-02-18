(ns backend.users
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema User
  {:id s/Int
   :name s/Str
   :role s/Keyword})

(p/defnk ^:query list-users
  {:load-current-user true
   :requires-role #{:admin}}
  [[:state users]
   [:entities [:current-user role]]]
  (success (vals @users)))
