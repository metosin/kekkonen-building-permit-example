(ns frontend.main
  (:require-macros [cljs.core.async.macros :as a])
  (:require [reagent.core :as r]
            [cljs.core.async :as a]
            [frontend.app :as app]
            [reagent-dev-tools.core :as dev]
            [devtools.core :as devtools]
            [metosin.dates :as dates]
            [frontend.chord :as chord]))

(devtools/enable-feature! :sanity-hints)
(devtools/install!)

(defn date-str [x]
  (if x (dates/format x {:pattern "d.M.yyyy HH:mm"})))

(def authority? #{:authority})
(def applicant? #{:applicant})

(defmulti render-view :routing/id)

(defn login-view []
  (let [username (r/atom "")]
    (fn []
      [:div.col-md-4.col-md-offset-4
       [:h1 "Hello, please log in"]
       [:form
        {:on-submit (fn [e]
                      (.preventDefault e)
                      (app/login! @username))}
        [:div.form-group
         [:input.form-control
          {:value @username
           :placeholder "username"
           :on-change #(reset! username (.. % -target -value))}]]
        [:button.btn.btn-primary.btn-block
         {:type "submit"}
         "Login"]

        [:p "Demo users: applicant-1, applicant-2, authority-1, authority-2, admin"]]])))

(defn permits-view []
  (let [role @(r/track app/current-role)
        permits (vals @(r/track app/permits))]
    [:div
     [:h1 "Your building permits"
      [:div.btn-group.pull-right
       (if (applicant? role)
         [:button.btn.btn-success
          {:type "button"
           :on-click #(app/navigate! :new-permit nil)}
          "New permit"])]]
     [:table.table.table-bordered
      [:thead
       [:tr
        [:th "Title"]
        [:th "State"]
        (if (authority? role)
          [:th "Applicant"])
        (if (applicant? role)
          [:th "Handler"])
        [:th "Created"]]]
      [:tbody
       (for [{:keys [permit-id title applicant-id applicant authority-id authority created state] :as permit} permits]
         [:tr
          {:key permit-id}
          [:td
           [:a {:on-click #(app/navigate! :permit {:permit-id permit-id})
                :href "#"}
            title]]
          [:td
           (name state)]
          (if (authority? role)
            [:td (:name applicant)])
          (if (applicant? role)
            [:td (if authority-id
                   (:name authority)
                   "Not assigned yet")])
          [:td (date-str created)]])]]]))

(defn new-permit-view []
  (let [data (r/atom {:title ""})]
    (fn []
      [:div
       [:h1 "New permit"]
       [:form
        {:on-submit (fn [e]
                      (.preventDefault e)
                      (app/create-permit! @data))}
        [:div.form-group
         [:label "Title"]
         [:input.form-control
          {:value (:title @data)
           :on-change #(swap! data assoc :title (.. % -target -value))}]]

        [:div.btn-group
         [:button.btn.btn-success
          {:type "submit"}
          "Create"]]]])))

(defmulti action (fn [k permit] k))

(defmethod action :default [k _]
  [:h2 "Unknown action " (name k)])

(defmethod action :building-permit/open [k {:keys [permit-id]}]
  [:div
   [:button.btn.btn-success
    {:on-click #(app/permit-action! k {:permit-id permit-id})}
    "Open"]])

(defmethod action :building-permit/submit [k {:keys [permit-id]}]
  [:div
   [:button.btn.btn-success
    {:on-click #(app/permit-action! k {:permit-id permit-id})}
    "Submit"]])

(defmethod action :building-permit/claim [k {:keys [permit-id]}]
  [:div
   [:button.btn.btn-success
    {:on-click #(app/permit-action! k {:permit-id permit-id})}
    "Claim"]])

(defmethod action :building-permit/return-to-applicant [k {:keys [permit-id]}]
  [:div
   [:button.btn.btn-success
    {:on-click #(app/permit-action! k {:permit-id permit-id})}
    "Return to applicant"]])

(defmethod action :building-permit/approve [k {:keys [permit-id]}]
  [:div
   [:button.btn.btn-success
    {:on-click #(app/permit-action! k {:permit-id permit-id})}
    "Approve"]])

(defmethod action :building-permit/reject [k {:keys [permit-id]}]
  [:div
   [:button.btn.btn-success
    {:on-click #(app/permit-action! k {:permit-id permit-id})}
    "Reject"]])

(defn permit-view [_]
  (let [comment-text (r/atom "")]
    (fn [{:keys [permit-id]}]
      (let [{:keys [title applicant authority created permit-id comments state] :as permit}
            @(r/track app/permit-by-id permit-id)]
        [:div
         [:h1 "Permit: " title]

         [:div.row
          [:dl.col-sm-4
           [:dt "Applicant"]
           [:dd (:name applicant)]

           [:dt "Authority person"]
           [:dd (:name authority)]

           [:dt "Created"]
           [:dd (date-str created)]]]

         [:ul.state-list
          [:li {:class (if (#{:draft :open :submitted :approved :rejected} state) "active ")}
           "Draft"]
          [:li {:class (if (#{:open :submitted :approved :rejected} state) "active ")}
           "Open"]
          [:li {:class (if (#{:submitted :approved :rejected} state) "active ")}
           "Submitted"]
          [:li {:class (if (#{:approved :rejected} state) "active ")}
           "Approved / Rejected"]]

         [:h2 "Available actions"]

         (for [k @(r/track app/interesting-actions)]
           ^{:key k}
           [action k permit])

         [:h2 "Comments"]

         (if @(r/track app/available-action? :building-permit/add-comment)
           [:form
            {:on-submit (fn [e]
                          (.preventDefault e)
                          (app/permit-action! :building-permit/add-comment {:permit-id permit-id
                                                                            :text @comment-text}))}
            [:div.form-group
             [:textarea.form-control
              {:value @comment-text
               :on-change #(reset! comment-text (.. % -target -value))}]]
            [:button.btn.btn-primary.btn-block
             {:type "submit"}
             "Comment"]])

         (for [{:keys [sent text user-id]} comments]
           [:div.comment
            [:h2 (date-str sent) " - " user-id]
            [:p text]])]))))

(defmethod render-view :default [_]
  [:h1 "Unknown view"])

(defmethod app/navigate-hook :permits [_]
  (app/load-my-permits!))

(defmethod render-view :permits [_]
  [permits-view])

(defmethod render-view :new-permit [_]
  [new-permit-view])

(defmethod app/navigate-hook :permit [{:keys [permit-id]}]
  (app/check-available-permit-actions! {:permit-id permit-id}))

(defmethod render-view :permit [{:keys [permit-id]}]
  [permit-view {:permit-id permit-id}])

(defn main-view []
  (let [view @(r/track app/view)
        session @(r/track app/session)]
    [:div
     [:nav.navbar.navbar-static-top.navbar-default
      [:div.container
       [:div.navbar-header
        [:a.navbar-brand
         {:href "#"
          :on-click  #(app/navigate! :permits)}
         "Building Permit Application"]]
       [:ul.nav.navbar-nav
        [:li
         [:a {:on-click #(app/navigate! :permits)
              :href "#"}
          "My permits"]]]
       (if (:user session)
         [:ul.nav.navbar-nav.navbar-right
          [:li
           [:a
            {:href "#"
             :on-click #(app/logout!)}
            "Logged in as " [:strong (:name (:user session))] ", log out"]]])]]
     [:div.container
      (let [session-status (:status session)]
        (if (= session-status :logged)
          (render-view view)
          (case session-status
            :not-logged [login-view]
            [:div "wat..."])))]]))

(defn init! []
  (a/go
    (a/<! (app/load-session!))
    (app/navigate! :permits))
  (r/render [main-view] (js/document.getElementById "app"))
  (chord/connect nil)
  (if-let [el (js/document.getElementById "dev")]
    (r/render [dev/dev-tool {}] el)))

(init!)
