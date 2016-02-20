(ns backend.building-permit
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]
            [backend.chord :as chord]))

(s/defschema Roles (s/enum :applicant :authority :admin))

(s/defschema States (s/enum :draft :open :submitted :approved :rejected))

(s/defschema Event
  {:type (s/enum :comment :transition)
   (s/optional-key :from-state) s/Keyword
   (s/optional-key :to-state) s/Keyword
   :user s/Int
   :sent s/Inst
   (s/optional-key :text) s/Str})

(s/defschema BuildingPermit
  {:permit-id s/Int
   (s/optional-key :archive-id) s/Int
   :state States
   :title s/Str
   :applicant-id s/Int
   :created s/Inst
   :authority-id (s/maybe s/Int)
   :events [Event]})

(s/defschema NewBuildingPermit
  (st/select-keys BuildingPermit [:title]))

(defn has-permission? [permit user]
  (let [{:keys [applicant-id authority-id]} permit
        {:keys [user-id role]} user]
    (case role
      :applicant (= user-id applicant-id)
      :authority true
      :admin true)))

(defn is-interesting? [permit user]
  (let [{:keys [applicant-id authority-id]} permit
        {:keys [user-id role]} user]
    (case role
      :applicant (= user-id applicant-id)
      :authority (or (= user-id authority-id) (nil? authority-id))
      :admin true)))

(defn enrich [state permit]
  (assoc permit
         :authority (get @(:users state) (:authority-id permit))
         :applicant (get @(:users state) (:applicant-id permit))))

(defn retrieve-permit [_]
  (p/fnk [[:state permits :as state]
          [:entities current-user]
          [:data permit-id :- s/Int]
          :as context]
    (if-let [permit (get @permits permit-id)]
      (if (has-permission? permit current-user)
        (assoc-in context [:entities :permit] permit)
        (failure! {:error :unauthorized}))
      (failure! {:error :no-permit}))))

(defn requires-state [allowed-states]
  (p/fnk [[:entities [:permit state]] :as context]
    (if (allowed-states state)
      context
      (failure! {:error :bad-state
                 :allowed-states allowed-states
                 :current-state state}))))

(defn requires-claim [v]
  (p/fnk [[:entities
           [:permit authority-id]
           [:current-user user-id]]
          :as context]
    (if (case v
          true (= authority-id user-id)
          :no (nil? authority-id))
      context
      (failure! {:error (case v
                          true :requires-claim
                          :no :requires-no-claim)}))))

(def broadcast-update
  {:leave (p/fnk [chord [:data permit-id :- s/Int]]
            (chord/broadcast chord {:permit-id permit-id}))})

(defn add-comment' [permit new-comment]
  (update permit :events (fnil conj []) new-comment))

(defn set-state [permit new-state]
  (-> permit
      (assoc :state new-state)
      (add-comment' {:type :transition
                     :from-state (:state permit)
                     :to-state new-state
                     :user nil
                     :sent (java.util.Date.)})))

(p/defnk ^:query get-permit
  "Retrieve a single building permit"
  {::retrieve-permit true
   :responses {:default {:schema BuildingPermit}}}
  [[:entities permit]
   state]
  (success (enrich state permit)))

(p/defnk ^:query my-permits
  "List building permits you have access to"
  {:responses {:default {:schema [BuildingPermit]}}}
  [[:state permits :as state]
   [:entities current-user]]
  (success (->> @permits
                vals
                (filter #(is-interesting? % current-user))
                (map #(enrich state %))
                vec)))

(p/defnk ^:command create-permit
  "Create building permit application"
  {:requires-role #{:applicant}}
  [[:state permits id-seq]
   [:entities current-user]
   chord
   data :- NewBuildingPermit]
  (let [permit-id (swap! id-seq inc)
        permit (-> data
                   (assoc :state :draft
                          :permit-id permit-id
                          :applicant-id (:user-id current-user)
                          :created (java.util.Date.)
                          :authority-id nil
                          :events [])
                   (->> (s/validate BuildingPermit)))]
    (swap! permits assoc permit-id permit)
    (chord/broadcast chord {:permit-id permit-id})
    (success {:permit-id permit-id})))

(p/defnk ^:command open
  "Ask authority for help"
  {:requires-role #{:applicant}
   ::retrieve-permit true
   ::requires-state #{:draft}
   :interceptors [broadcast-update]}
  [[:entities [:permit permit-id]]
   [:state permits]]
  (swap! permits update permit-id set-state :open)
  (success {:status :ok}))

(p/defnk ^:command submit
  "Submit the permit for official review"
  {:requires-role #{:applicant}
   ::retrieve-permit true
   ::requires-state #{:open :draft}
   :interceptors [broadcast-update]}
  [[:state permits]
   [:entities [:permit permit-id]]]
  (swap! permits update permit-id set-state :submitted)
  (success {:status :ok}))

(p/defnk ^:command claim
  "Claim this permit"
  {:requires-role #{:authority}
   ::requires-claim :no
   ::retrieve-permit true
   :interceptors [broadcast-update]}
  [[:state permits]
   [:entities
    [:permit permit-id]
    [:current-user user-id]]]
  (swap! permits update permit-id assoc :authority-id user-id)
  (success {:status :ok}))

(p/defnk ^:command return-to-applicant
  "Ask the applicant to fix something"
  {:requires-role #{:authority}
   ::requires-claim true
   ::retrieve-permit true
   ::requires-state #{:submitted}
   :interceptors [broadcast-update]}
  [[:state permits]
   [:entities [:permit permit-id]]]
  (swap! permits update permit-id set-state :open)
  (success {:status :ok}))

(p/defnk ^:command approve
  "Approve a permit"
  {:requires-role #{:authority}
   ::requires-claim true
   ::retrieve-permit true
   ::requires-state #{:submitted}
   :interceptors [broadcast-update]}
  [[:state permits archive-id-seq]
   [:entities [:permit permit-id]]]
  (swap! permits update permit-id assoc
         :state :approved
         :archive-id (swap! archive-id-seq inc))
  (success {:status :ok}))

(p/defnk ^:command reject
  "Reject a permit"
  {:requires-role #{:authority}
   ::requires-claim true
   ::retrieve-permit true
   ::requires-state #{:submitted}
   :interceptors [broadcast-update]}
  [[:state permits]
   [:entities [:permit permit-id]]]
  (swap! permits update permit-id set-state :rejected)
  (success {:status :ok}))

(p/defnk ^:command add-comment
  "Add a comment to permit"
  {::retrieve-permit true
   ::requires-state (complement #{:approved :rejected})
   ::requires-claim true
   :requires-role #{:applicant :authority}
   :interceptors [broadcast-update]}
  [[:data text :- s/Str]
   [:state permits]
   [:entities
    [:permit permit-id]
    [:current-user user-id]]]
  (let [new-comment {:type :comment
                     :user-id user-id
                     :sent (java.util.Date.)
                     :text text}]
    (swap! permits update permit-id add-comment' new-comment)
    (success {:status :ok})))
