(ns kekkonen.client.cqrs
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs-http.core :as http-core]
            [cljs.core.async :as async :refer [<!]]
            [clojure.string :as string]))

(defn action->uri [action]
  {:pre [(keyword? action)]}
  (string/replace (string/join "/" ((juxt namespace name) action)) #"\." "/"))

(defn create-client [{:keys [base-uri] :as opts}]
  opts)

(declare invoke query command)

(defn wrap-request [req]
  (-> req
      http/wrap-request))

(def request (wrap-request http-core/request))

(defn invoke
  ([type client action]
   (invoke type client action nil))
  ([type client action data]
   (request
     {:url (str (:base-uri client) "/" (action->uri action))
      :method (if (= :command type) :post :get)
      (if (= :command type) :transit-params :query-params) data
      :headers {"kekkonen.mode" "invoke"}
      :accept "application/transit+json"
      :kekkonen {:client client
                 :action action
                 :data data}})))

(def command (partial invoke :command))
(def query (partial invoke :query))
